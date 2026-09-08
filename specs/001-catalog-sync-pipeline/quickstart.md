# Quickstart: проверка ETL-пайплайна «Источник → Витрина товаров»

Сквозная проверка фичи. Модель данных — [data-model.md](data-model.md), REST-контракт —
[contracts/operational-api.md](contracts/operational-api.md). Соответствует plan.md
(Maven, Docker Compose, два контейнера PostgreSQL).

## Пререквизиты

- JDK 21, Docker + Docker Compose.
- Окружение: `docker compose up -d` поднимает `db-source` и `db-mart`; Flyway накатывает
  `db/migration/source` и `db/migration/mart` соответственно.
- Тестовые данные: `scripts/seed_source.sql` наполняет `db-source.products` (1 млн строк
  для perf-сценария, меньше — для ручных проверок).

## Сборка и тесты

```bash
./mvnw verify            # компиляция + unit + Testcontainers-тесты
./mvnw spotless:check    # формат
```

Ожидаемо: зелёные `IncrementalSyncTest`, `CrashRecoveryTest`, `OutOfOrderTest`,
`BrokenRecordTest`, `ReconciliationTest` (маппинг на SC — plan.md §7).
`PerformanceTest` — отдельный профиль, запуск по требованию:

```bash
./mvnw verify -Pperf
```

## Локальный запуск

```bash
docker compose up -d     # db-source, db-mart
./mvnw spring-boot:run
```

## Сценарии валидации

### 1. Первичная загрузка и инкремент (US-1, SC-2)

1. Наполнить `db-source.products` (например, 10k записей).
2. Запустить прогон: `POST /api/etl/run` → `{ runId }`; дождаться `SUCCESS` через
   `GET /api/etl/run/{id}`.
3. Проверить: `count(*)` в `products_mart` равен источнику; `staging_raw` содержит все
   записи прогона (US-5).
4. Изменить/добавить записи в источнике, снова запустить прогон → перенесены только они;
   третий (пустой) запуск → `rowsLoaded = 0`, витрина не изменилась.

### 2. Восстановление после сбоя (US-2, SC-3)

1. Во время прогона большого объёма прервать приложение между батчами
   (`CrashRecoveryTest` делает это через interrupt; вручную — kill процесса).
2. Перезапустить, запустить прогон снова.
3. Проверить сверкой: `POST /api/etl/reconcile` → `mismatch: 0`; дублей по `sku` нет.

### 3. Защита от устаревших версий (US-3)

1. В витрине товар с `updated_at = T2`.
2. Прогнать батч, содержащий версию этого товара с `updated_at = T1 < T2`.
3. Проверить: в `products_mart` осталась версия T2 (`OutOfOrderTest`).

### 4. Битые записи (US-5, SC-5)

1. В источнике 1000 записей, 5 с отрицательной ценой.
2. Прогон → в `products_mart` 995 записей, в `etl_error` 5 записей с `reason`,
   прогон `SUCCESS`, `rows_loaded = 995`, `rows_failed = 5`.

### 5. Реконсиляция (US-5, SC-4)

1. Удалить 100 записей из `products_mart` минуя пайплайн.
2. `POST /api/etl/reconcile` → `mismatch: 100`.

### 6. Производительность (SC-1)

1. `scripts/seed_source.sql` — 1 млн строк в источник.
2. Полный прогон с нулевого чекпоинта, замер времени (`PerformanceTest`, профиль `perf`).
3. Критерий: укладывается в ночное окно (≤4 ч).

## Критерии приёмки

Все сценарии проходят; каждый прогон виден в `etl_run` со счётчиками; ключевые события —
в структурных JSON-логах; к источнику выполняются только курсорные чтения по индексу
`(updated_at, sku)`.
