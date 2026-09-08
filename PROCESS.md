# PROCESS.md — журнал итераций ETL-пайплайна

- 2026-09-08 | Phase 1 (T001–T004, Setup) | OK | `./gradlew spotlessApply build` — зелёный.
  Нюансы: настройки T004 влиты в существующий `application.yaml` (дубль `application.yml`
  не создавался); Flyway отключён до Phase 2 (миграции per-datasource). Замечание:
  `.kimi-code/AGENTS.md` ссылается на Maven/`./mvnw` — проект на Gradle (`./gradlew`),
  команды Maven неприменимы.

- 2026-09-08 | Phase 2 (T005–T010, Foundational) | OK | `./gradlew spotlessApply build`
  с тестами — зелёный (Testcontainers: 2× PostgreSQL 17, миграции обеих БД проходят).
  Поломки и починка:
  1. Testcontainers без версии (Boot 4 не управляет) → добавлен `testcontainers-bom:1.21.3`.
  2. `@Testcontainers`-расширение дёргало mapped-порты до старта контейнеров → ручной
     старт в static-блоке `AbstractIntegrationTest` (singleton-контейнеры).
  3. Hikari `readOnly=true` на источнике ломал Flyway-миграцию (`25006 read-only
     transaction`) → Flyway источника работает через DelegatingDataSource-обёртку,
     снимающую read-only; пул для пайплайна остаётся read-only.
  Отклонение от tasks.md: T010 — вместо logback-spring.xml + logstash-encoder
  (новая зависимость запрещена правилами без подтверждения) включён нативный
  structured logging Spring Boot (`logging.structured.format.console: logstash`).

- 2026-09-08 | Phase 3 (T011–T019, US1 MVP) | OK | `IncrementalSyncTest` (4 теста) зелёный:
  перенос новых/изменённых записей, пустой запуск — 0 строк (SC-2), tie-breaker по sku
  при одинаковых updated_at. Поломки и починка:
  1. Jackson отсутствует в classpath (Boot 4 webmvc его не тянет) → payload для
     staging_raw сериализуется вручную в `StagingRepo` (простые поля, экранирование
     кавычек); новых зависимостей не добавлял.
  2. Тестовые insert'ы передавали цену строкой в `numeric` → литерал/каст `?::numeric`
     в тестовых хелперах.

- 2026-09-08 | Phase 4 (T020–T023, US2 recovery) | OK | `CrashRecoveryTest` (2 теста)
  зелёный: interrupt между батчами → прогон FAILED, рестарт с чекпоинта — 0 потерь,
  0 дублей (SC-3); interrupt до первого батча → FAILED без загрузки. Реализация:
  проверка `Thread.isInterrupted()` между батчами в `EtlPipeline.run()` +
  `ApplicationRunner`-хук `failOrphanedRuns` (осиротевшие RUNNING → FAILED при старте).
  Поломок не было.

- 2026-09-08 | Phase 5 (T024–T026, US3 защита версий) | OK | `OutOfOrderTest` (2 теста)
  зелёный: старая версия не перезаписывает новую, повтор равной версии — no-op (FR-4).
  Реализация: `WHERE products_mart.updated_at < EXCLUDED.updated_at` в upsert `MartRepo`.
  Поломок не было.

- 2026-09-08 | Phase 6 (T027–T031, US4 REST/расписание) | OK | `EtlApiTest` (3 теста)
  зелёный: POST /api/etl/run → runId, GET /api/etl/run/{id} → статус/счётчики,
  активный прогон → 409, неизвестный id → 404 (FR-7, FR-8). Реализация:
  - `EtlPipeline` разделён на `beginRun`/`executeRun`; защита от конкурентных запусков —
    атомарный `INSERT ... WHERE NOT EXISTS (RUNNING)` в `RunRepo.tryStart` +
    `RunConflictException` → 409.
  - POST запускает прогон асинхронно через `applicationTaskExecutor`, возвращает runId.
  - `ScheduledEtlRunner` через `SchedulingConfigurer` (fixedDelay = `etl.poll-interval`,
    Duration); в тестах отключён свойством `etl.scheduling.enabled=false`.
  Поломок не было. Замечание: JSON в Boot 4 — Jackson 3 (`tools.jackson`), поэтому
  ручная сериализация payload в `StagingRepo` (Phase 3) была необходимой.
