# План реализации: ETL-пайплайн «Источник → Витрина товаров»

> Формат: Spec-Kit `/plan`. Вход: [spec.md](spec.md) (FR-1…FR-8, SC-1…SC-5).
> Здесь — КАК: стек, архитектура, модель данных, контракты, разбивка на компоненты.

## 1. Summary

Периодический инкрементальный ETL: читаем изменения каталога из источника
по курсору `updated_at`, складываем сырые данные в staging (аудит),
идемпотентно загружаем в витрину upsert'ом с защитой от устаревших версий,
продвигаем чекпоинт. Отдельная джоба реконсиляции сверяет источник и витрину.
Всё — одно Spring Boot приложение с двумя DataSource, поднятое в Docker Compose.

## 2. Technical Context

- **Язык/платформа**: Java 25, Spring Boot 4.x
- **Сборка**: Gradle (gradle)
- **Хранилище**: PostgreSQL 17 — два контейнера (`db-source`, `db-mart`),
  что честно имитирует две разные системы (FR-1, US-5)
- **Миграции**: Flyway, отдельные наборы миграций на каждую БД
- **Планировщик**: Spring `@Scheduled` (US-4); ручной запуск через
  REST-эндпоинт для crash-тестов (SC-3)
- **Тестирование**: JUnit 5 + Testcontainers (два PG-контейнера)
- **Мониторинг (FR-8)**: структурные логи (JSON) + таблица `etl_run`
- **Линт/формат**: Spotless
- **Оркестрация окружения**: Docker Compose

### Обоснование выбора (пригодится на собеседовании)

| Решение | Альтернатива | Почему выбрано |
|---|---|---|
| Spring `@Scheduled` | Quartz, внешний cron | скоуп — локальный прогон; Quartz — избыточная сложность |
| Курсор `updated_at` | CDC (Debezium) | CDC не поднять за практическую задачу; курсор покрывает FR-1/FR-2 |
| Upsert в PG (`ON CONFLICT`) | merge-логика в коде | идемпотентность атомарно на уровне БД (FR-3, FR-4) |
| Два контейнера PG | две схемы в одной БД | честная изоляция «чужой» системы, отдельные миграции |

## 3. Архитектура и поток данных

```
┌────────────┐  cursor=last_updated_at   ┌──────────────────────────────┐
│  db-source  │ ───────────────────────▶ │  EtlPipeline (@Scheduled/REST)│
│ (products)  │  батчи по N записей       │                              │
└────────────┘                           │  1. extract  (read-only)     │
                                         │  2. stage    → staging_raw   │
┌────────────┐                           │  3. load     → upsert в mart │
│  db-mart    │ ◀─────────────────────── │  4. advance  → checkpoint    │
│ staging_raw │   запись в транзакциях    └──────────────────────────────┘
│ products_   │                                        │
│ mart,       │                           ┌────────────▼─────────────┐
│ checkpoint, │                           │ ReconciliationJob         │
│ etl_run     │                           │ (cron 03:00 / REST)       │
└────────────┘ ◀──── count-сверка ─────── └──────────────────────────┘
```

Правила потока (связка с требованиями):

- Каждый батч: staging-insert → mart-upsert → checkpoint-advance выполняются
  **в одной транзакции на db-mart** (FR-2, FR-3). Extract — read-only,
  транзакция источника не держится.
- Идемпотентность: upsert по PK `sku` с условием
  `WHERE products_mart.updated_at < EXCLUDED.updated_at` (FR-4, US-3).
- Битая запись: валидация до upsert, в `etl_error` + исключение из батча
  (FR-6); батч не прерывается.
- Курсор с миллисекундной гранулярностью: выборка `updated_at > :cursor`
    + tie-breaker по `sku` — пар `(updated_at, sku)` — чтобы не потерять
      записи с одинаковой отметкой (граничный случай из спеки).

## 4. Модель данных

### db-source (миграции `source/V*.sql`)

```sql
CREATE TABLE products (
    sku        TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    price      NUMERIC(12,2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_updated ON products (updated_at, sku); -- под курсор
```

Наполнение: `scripts/seed_source.sql` — 1 млн строк через `generate_series`
(нужно для SC-1).

### db-mart (миграции `mart/V*.sql`)

```sql
CREATE TABLE staging_raw (               -- FR-5, US-5: аудит «как есть»
    id         BIGSERIAL PRIMARY KEY,
    run_id     BIGINT NOT NULL,
    sku        TEXT NOT NULL,
    payload    JSONB NOT NULL,           -- сырой снимок записи
    staged_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_staging_run ON staging_raw (run_id);

CREATE TABLE products_mart (
    sku        TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    price      NUMERIC(12,2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE etl_checkpoint (            -- FR-2
    pipeline    TEXT PRIMARY KEY,
    last_cursor TIMESTAMPTZ NOT NULL,
    last_sku    TEXT NOT NULL DEFAULT '' -- tie-breaker курсора
);

CREATE TABLE etl_run (                   -- FR-8
    id           BIGSERIAL PRIMARY KEY,
    pipeline     TEXT NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    finished_at  TIMESTAMPTZ,
    status       TEXT NOT NULL,          -- RUNNING / SUCCESS / FAILED
    rows_staged  BIGINT DEFAULT 0,
    rows_loaded  BIGINT DEFAULT 0,
    rows_failed  BIGINT DEFAULT 0
);

CREATE TABLE etl_error (                 -- FR-6
    id        BIGSERIAL PRIMARY KEY,
    run_id    BIGINT NOT NULL,
    sku       TEXT,
    payload   JSONB,
    reason    TEXT NOT NULL
);
```

## 5. Контракты (REST для ручного управления и тестов)

```
POST /api/etl/run            → { runId }        запуск пайплайна (для crash-теста SC-3)
GET  /api/etl/run/{id}       → { status, rowsStaged, rowsLoaded, rowsFailed }
POST /api/etl/reconcile      → { sourceCount, martCount, mismatch, details }
```

Внутренние интерфейсы:

```java
public interface SourceReader {
    List<SourceProduct> fetchSince(Instant cursor, String afterSku, int limit);
}
public interface MartLoader {          // staging + upsert + checkpoint атомарно
    BatchResult loadBatch(long runId, List<SourceProduct> batch);
}
public interface Reconciler {
    ReconcileReport reconcile();
}
```

## 6. Структура проекта

```
dashboard/
├── specs/
│   └── 001-catalog-sync-pipeline
│       ├── spec.md                      # есть (фаза /specify)
│       ├── plan.md                      # этот файл (фаза /plan)
│       ├── tasks.md                     # следующая фаза (/tasks)
│       └── ...                          # другие папки проекта
├── docker-compose.yml           # db-source, db-mart
├── build.gradle
├── src/main/java/com/example/dashboard/
│   ├── EtlApplication.java
│   ├── config/      SourceDbConfig, MartDbConfig   # два DataSource
│   ├── source/      SourceReader, SourceProduct
│   ├── pipeline/    EtlPipeline, BatchResult
│   ├── mart/        MartLoader, StagingRepo, CheckpointRepo, RunRepo, ErrorRepo
│   ├── reconcile/   Reconciler, ReconcileReport
│   └── api/         EtlController
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/{source,mart}/
├── scripts/seed_source.sql
└── src/test/java/...            # Testcontainers-тесты (см. раздел 7)
```

## 7. Стратегия тестирования (маппинг на критерии успеха)

| Тест | Проверяет |
|---|---|
| `IncrementalSyncTest` | US-1: новые/изменённые товары переносятся, пустой запуск — ничего не меняет (SC-2) |
| `CrashRecoveryTest` | SC-3: старт → прерывание между батчами (отмена через Thread interrupt) → рестарт → сверка «0 дублей, 0 потерь» |
| `OutOfOrderTest` | US-3: запись со старым `updated_at` не перезаписывает новую |
| `BrokenRecordTest` | SC-5: 1000 записей, 5 с отрицательной ценой → 995 в витрине, 5 в `etl_error` |
| `ReconciliationTest` | SC-4: удалить 100 записей из витрины → сверка возвращает mismatch=100 |
| `PerformanceTest` (отдельный профиль) | SC-1: seed 1 млн строк, полный прогон, замер времени |

## 8. Ключевые фрагменты реализации (референс)

**Курсорная выборка (источник):**

```java
public List<SourceProduct> fetchSince(Instant cursor, String afterSku, int limit) {
    return jdbc.query("""
        SELECT sku, name, price, updated_at FROM products
        WHERE (updated_at, sku) > (?, ?)
        ORDER BY updated_at, sku
        LIMIT ?
        """, rowMapper, cursor, afterSku, limit);
}
```

**Атомарный батч (витрина):**

```java
@Transactional("martTransactionManager")
public BatchResult loadBatch(long runId, List<SourceProduct> batch) {
    stagingRepo.insertAll(runId, batch);                       // аудит
    var valid = batch.stream().filter(this::validate).toList();
    var broken = batch.stream().filter(b -> !validate(b)).toList();
    errorRepo.insertAll(runId, broken);                        // FR-6
    martRepo.upsertAll(valid);                                 // ON CONFLICT ... WHERE старее
    checkpointRepo.advance(maxCursor(valid));                  // FR-2
    runRepo.addStats(runId, batch.size(), valid.size(), broken.size());
    return new BatchResult(valid.size(), broken.size());
}
```

**Upsert с защитой от устаревших версий:**

```sql
INSERT INTO products_mart (sku, name, price, updated_at)
VALUES (?, ?, ?, ?)
ON CONFLICT (sku) DO UPDATE
SET name = EXCLUDED.name, price = EXCLUDED.price, updated_at = EXCLUDED.updated_at
WHERE products_mart.updated_at < EXCLUDED.updated_at;   -- FR-4
```

## 9. Риски и компромиссы

- **Риск**: курсор по `updated_at` пропустит запись, изменённую в долгой
  транзакции, закоммиченной после прохода курсора. **Митигируется** окном
  отставания (читаем `updated_at < now() - interval '30 seconds'`); на
  собеседовании упомянуть, что прод-решение — CDC.
- **Компромисс**: reconcile по count, а не по хэшам строк — дешевле;
  поэлементная сверка отнесена в backlog.
- **Компромисс**: один инстанс пайплайна; конкурентные запуски исключены
  блокировкой (`etl_run.status = RUNNING` + advisory lock).

## 10. Готовность к фазе /tasks

План закрывает все FR-1…FR-8 и SC-1…SC-5 из spec.md. Следующий шаг —
`tasks.md`: разбивка на ~12 задач с зависимостями и критериями готовности
(миграции → SourceReader → MartLoader → Pipeline → API → Reconciler →
тесты → seed/perf).
