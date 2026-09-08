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
