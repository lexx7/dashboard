# Data Model: ETL-пайплайн «Источник → Витрина товаров»

Две физические БД (отдельные контейнеры PostgreSQL 17): `db-source` (read-only для
пайплайна) и `db-mart` (витрина + служебные таблицы). Схемы версионируются Flyway:
`db/migration/source/V*.sql` и `db/migration/mart/V*.sql`. DDL соответствует plan.md §4.

## db-source

### products — каталог источника (контракт чтения)

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| sku | TEXT | PK | Стабильный идентификатор товара |
| name | TEXT | NOT NULL | Название |
| price | NUMERIC(12,2) | NOT NULL | Цена; отрицательная — битая запись (FR-6) |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Поднимается при любом изменении |

Индекс: `ix_products_updated (updated_at, sku)` — под курсорную выборку (FR-1).

**Инварианты**: append/update-only (без DELETE в v1); любое изменение поднимает
`updated_at`. Наполнение для тестов/perf: `scripts/seed_source.sql` — 1 млн строк через
`generate_series` (SC-1).

## db-mart

### staging_raw — аудит сырых записей (FR-5)

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| id | BIGSERIAL | PK | |
| run_id | BIGINT | NOT NULL, FK-логика к etl_run | Прогон-владелец |
| sku | TEXT | NOT NULL | Идентификатор товара |
| payload | JSONB | NOT NULL | Сырой снимок записи «как есть» |
| staged_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Время чтения |

Индекс: `ix_staging_run (run_id)`.

### products_mart — витрина

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| sku | TEXT | PK | Соответствует `source.products.sku` |
| name | TEXT | NOT NULL | |
| price | NUMERIC(12,2) | NOT NULL | Только валидные значения (FR-6) |
| updated_at | TIMESTAMPTZ | NOT NULL | Метка изменения из источника |

Правила:
- upsert по PK `sku`; обновление только если `products_mart.updated_at <
  EXCLUDED.updated_at` — защита от устаревших версий (FR-4, US-3).
- В v1 удаления отсутствуют (источник append/update-only).

### etl_checkpoint — позиция синхронизации (FR-2)

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| pipeline | TEXT | PK | Имя пайплайна (singleton на пайплайн) |
| last_cursor | TIMESTAMPTZ | NOT NULL | Курсор по `updated_at` |
| last_sku | TEXT | NOT NULL DEFAULT '' | Tie-breaker при равных метках |

Переходы состояния: курсор монотонно растёт по `(last_cursor, last_sku)`; продвигается
только в транзакции успешного батча (атомарно с загрузкой).

### etl_run — журнал прогонов (FR-8)

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| id | BIGSERIAL | PK | Идентификатор прогона |
| pipeline | TEXT | NOT NULL | |
| started_at | TIMESTAMPTZ | NOT NULL | |
| finished_at | TIMESTAMPTZ | nullable | NULL = прогон в работе |
| status | TEXT | NOT NULL | `RUNNING` → `SUCCESS` / `FAILED` |
| rows_staged | BIGINT | DEFAULT 0 | Прочитано из источника |
| rows_loaded | BIGINT | DEFAULT 0 | Загружено в витрину |
| rows_failed | BIGINT | DEFAULT 0 | Отклонено валидацией |

Инвариант: `rows_staged = rows_loaded + rows_failed` на завершённом прогоне.
Конкурентные запуски исключены: один `RUNNING` на пайплайн + advisory lock (FR-7).

### etl_error — журнал битых записей (FR-6)

| Поле | Тип | Ограничения | Комментарий |
|------|-----|-------------|-------------|
| id | BIGSERIAL | PK | |
| run_id | BIGINT | NOT NULL | Прогон-владелец |
| sku | TEXT | nullable | Может отсутствовать у совсем битой записи |
| payload | JSONB | nullable | Сырая запись |
| reason | TEXT | NOT NULL | Причина отклонения |

## Связи

```text
db-source.products ──(курсор (updated_at, sku), батчи)──▶ EtlPipeline
                                                             │
        db-mart: staging_raw ──┐                             │
                  etl_error ───┤── etl_run (1 прогон : N записей)
                  products_mart ┘                             │
                  etl_checkpoint ◀── advance в транзакции батча
                                                             │
        Reconciler: count(db-source.products) vs count(db-mart.products_mart)
```

## Объёмы

- `products` / `products_mart`: до ~1 млн строк (seed для SC-1).
- `staging_raw`: растёт на объём изменений за прогон; хранение истории — по умолчанию без
  ротации в локальном скоупе.
- `etl_run` / `etl_error`: по числу прогонов и битых записей.
