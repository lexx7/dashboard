---
description: "Task list for ETL-пайплайн «Источник → Витрина товаров»"
---

# Tasks: ETL-пайплайн «Источник → Витрина товаров»

**Input**: Design documents from `/specs/001-catalog-sync-pipeline/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/operational-api.md

**Tests**: Тесты включены — конституция (принцип II) требует их для каждого изменения,
а plan.md §7 маппит тесты на критерии успеха SC-1…SC-5.

**Organization**: Задачи сгруппированы по пользовательским историям спеки (US1–US5);
каждая фаза — независимо проверяемый инкремент.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно выполнять параллельно (разные файлы, нет незавершённых зависимостей)
- **[Story]**: US1–US5 по spec.md; только для фаз пользовательских историй

## Path Conventions

Реализация в текущем репозитории `dashboard/` (plan.md §6 после правки владельца):
Gradle, Java 25, Spring Boot 4.x, новый код в пакете `com.example.dashboard`.
Все пути ниже — от корня репозитория.

---

## Phase 1: Setup (инициализация проекта)

- [x] T001 Добавить зависимости в `build.gradle`: spring-boot-starter-jdbc, flyway-core, flyway-database-postgresql, postgresql (runtime); test: testcontainers (junit-jupiter, postgresql); добавить `@EnableScheduling` в существующий `src/main/java/com/example/dashboard/DashboardApplication.java` (новый application-класс НЕ создавать — plan.md §6)
- [x] T002 [P] Создать `docker-compose.yml` в корне репозитория: сервисы `db-source` и `db-mart` (PostgreSQL 17, отдельные порты и volume'ы)
- [x] T003 [P] Подключить плагин Spotless в `build.gradle` (конфигурация для Java)
- [x] T004 [P] Создать `src/main/resources/application.yml`: два DataSource (`source`, `mart`), размер батча (настраиваемый, по умолчанию 1000 — plan.md §3), окно отставания 30s, интервал опроса источника 60 секунд (plan.md §3) и cron сверки 03:00

---

## Phase 2: Foundational (блокирующие предпосылки)

**Purpose**: Схемы БД, конфигурация двух DataSource и базовая тестовая инфраструктура —
без них нельзя начинать ни одну историю.

- [x] T005 Миграция источника `src/main/resources/db/migration/source/V1__products.sql`: таблица `products(sku, name, price, updated_at)` + индекс `ix_products_updated (updated_at, sku)` (data-model.md)
- [x] T006 [P] Миграция витрины `src/main/resources/db/migration/mart/V1__mart_schema.sql`: `staging_raw`, `products_mart`, `etl_checkpoint`, `etl_run`, `etl_error` по data-model.md
- [x] T007 Конфигурация двух БД: `SourceDbConfig` (read-only) и `MartDbConfig` (`martTransactionManager`) в `src/main/java/com/example/dashboard/config/`
- [x] T008 [P] Типы данных: `SourceProduct` в `src/main/java/com/example/dashboard/source/`, `BatchResult` в `.../pipeline/`, `ReconcileReport` в `.../reconcile/`
- [x] T009 [P] Базовый класс интеграционных тестов с двумя PostgreSQL Testcontainers в `src/test/java/com/example/dashboard/AbstractIntegrationTest.java`
- [x] T010 [P] Структурное JSON-логирование (logback) в `src/main/resources/logback-spring.xml` (FR-8)

**Checkpoint**: инфраструктура готова — можно параллельно начинать истории.

---

## Phase 3: User Story 1 - Инкрементальный перенос изменений каталога (Priority: P1) 🎯 MVP

**Goal**: Пайплайн переносит новые и изменённые товары из источника в витрину батчами;
пустой запуск ничего не меняет (SC-2). Первичная загрузка — тот же код с нулевого
чекпоинта.

**Independent Test**: Наполнить источник, выполнить прогон, проверить равенство
витрины и источника; повторный пустой запуск — 0 загруженных записей.

### Tests for User Story 1

- [x] T011 [US1] Интеграционный `IncrementalSyncTest` в `src/test/java/com/example/dashboard/IncrementalSyncTest.java`: новые/изменённые записи переносятся, пустой запуск не меняет витрину, счётчики прогона корректны (SC-2). Тест падает до реализации.

### Implementation for User Story 1

- [x] T012 [P] [US1] `CheckpointRepo` в `src/main/java/com/example/dashboard/mart/CheckpointRepo.java`: чтение/продвижение курсора `(last_cursor, last_sku)` (FR-2)
- [x] T013 [P] [US1] `StagingRepo.insertAll` в `src/main/java/com/example/dashboard/mart/StagingRepo.java`: сохранение сырых записей с `run_id` (FR-5)
- [x] T014 [P] [US1] `RunRepo` в `src/main/java/com/example/dashboard/mart/RunRepo.java`: start/finish/addStats по `etl_run` (FR-8)
- [x] T015 [P] [US1] `MartRepo.upsertAll` в `src/main/java/com/example/dashboard/mart/MartRepo.java`: батчевый `INSERT ... ON CONFLICT (sku) DO UPDATE` (FR-3)
- [x] T016 [P] [US1] `SourceReader.fetchSince(Instant cursor, String afterSku, int limit)` в `src/main/java/com/example/dashboard/source/SourceReader.java`: курсорная выборка `(updated_at, sku) > (?, ?)` с окном отставания `updated_at < now() - 30s` (FR-1, research R2)
- [x] T017 [US1] `MartLoader.loadBatch` в `src/main/java/com/example/dashboard/mart/MartLoader.java`: `@Transactional("martTransactionManager")` — staging → upsert → checkpoint → статистика атомарно (FR-2, FR-3, plan.md §8)
- [x] T018 [US1] `EtlPipeline` в `src/main/java/com/example/dashboard/pipeline/EtlPipeline.java`: цикл батчей `extract → loadBatch` до исчерпания, создание/завершение прогона через `RunRepo` (FR-1, FR-8)
- [x] T019 [US1] Прогнать `src/test/java/com/example/dashboard/IncrementalSyncTest.java` до зелёного состояния

**Checkpoint**: US1 полностью работает и проверяется независимо (MVP).

---

## Phase 4: User Story 2 - Восстановление после сбоев (Priority: P2)

**Goal**: Прерывание между батчами не приводит к потерям и дублям; перезапуск продолжает
с последнего подтверждённого чекпоинта (SC-3).

**Independent Test**: Прервать прогон между батчами, перезапустить, сверка — 0 потерь,
0 дублей.

### Tests for User Story 2

- [x] T020 [US2] `CrashRecoveryTest` в `src/test/java/com/example/dashboard/CrashRecoveryTest.java`: старт прогона → interrupt между батчами → рестарт → сверка «0 дублей, 0 потерь» (SC-3). Тест падает до реализации.

### Implementation for User Story 2

- [x] T021 [US2] Обработка прерванных прогонов при старте приложения: осиротевшие `RUNNING` помечаются `FAILED`, чекпоинт остаётся на последнем подтверждённом батче — стартовый хук в `src/main/java/com/example/dashboard/pipeline/EtlPipeline.java`
- [x] T022 [US2] Корректная обработка прерывания потока (interrupt) между батчами: откат текущей транзакции, финализация прогона `FAILED` — в `src/main/java/com/example/dashboard/pipeline/EtlPipeline.java`
- [x] T023 [US2] Прогнать `src/test/java/com/example/dashboard/CrashRecoveryTest.java` до зелёного состояния

**Checkpoint**: US2 работает; crash-тест зелёный.

---

## Phase 5: User Story 3 - Устойчивость к устаревшим версиям записей (Priority: P2)

**Goal**: Версия с более старым `updated_at` не перезаписывает новую в витрине (FR-4).

**Independent Test**: Батч со старой версией после новой — в витрине остаётся новая.

### Tests for User Story 3

- [x] T024 [P] [US3] `OutOfOrderTest` в `src/test/java/com/example/dashboard/OutOfOrderTest.java`: запись со старым `updated_at` не перезаписывает новую; повтор той же версии — no-op. Тест падает до реализации.

### Implementation for User Story 3

- [x] T025 [US3] Добавить защиту от устаревших версий в upsert `src/main/java/com/example/dashboard/mart/MartRepo.java`: `WHERE products_mart.updated_at < EXCLUDED.updated_at` (FR-4, plan.md §8)
- [x] T026 [US3] Прогнать `src/test/java/com/example/dashboard/OutOfOrderTest.java` до зелёного состояния

**Checkpoint**: US3 работает; витрина защищена от «отката» версий.

---

## Phase 6: User Story 4 - Запуск по расписанию и вручную (Priority: P2)

**Goal**: Автоматический запуск по расписанию + REST для ручного запуска и статуса;
конкурентные запуски отклоняются (FR-7).

**Independent Test**: Дождаться срабатывания расписания и вызвать ручной запуск; оба
видны в `etl_run`; повторный запуск во время активного — 409.

### Tests for User Story 4

- [x] T027 [P] [US4] Интеграционный `EtlApiTest` в `src/test/java/com/example/dashboard/EtlApiTest.java`: `POST /api/etl/run` → 200 с `runId`; `GET /api/etl/run/{id}` → статус и счётчики; запуск во время активного прогона → 409; неизвестный id → 404. Тест падает до реализации.

### Implementation for User Story 4

- [x] T028 [US4] `EtlController` в `src/main/java/com/example/dashboard/api/EtlController.java`: `POST /api/etl/run`, `GET /api/etl/run/{id}` по contracts/operational-api.md (FR-7, FR-8)
- [x] T029 [US4] Защита от конкурентных запусков: advisory lock / проверка `RUNNING` в `src/main/java/com/example/dashboard/pipeline/EtlPipeline.java` (plan.md §9)
- [x] T030 [US4] `@Scheduled` триггер инкрементального прогона с интервалом 60 секунд (настраивается, plan.md §3) в `src/main/java/com/example/dashboard/pipeline/ScheduledEtlRunner.java` (FR-7)
- [x] T031 [US4] Прогнать `src/test/java/com/example/dashboard/EtlApiTest.java` до зелёного состояния

**Checkpoint**: US4 работает; пайплайн автономен и управляется через REST.

---

## Phase 7: User Story 5 - Аудит сырых данных и контроль расхождений (Priority: P3)

**Goal**: Валидация битых записей в `etl_error` без прерывания батча (FR-6); джоба
реконсиляции по count с REST-запуском (SC-4, SC-5).

**Independent Test**: 1000 записей с 5 битыми → 995 в витрине, 5 в `etl_error`;
удалить 100 записей из витрины → сверка возвращает `mismatch = 100`.

### Tests for User Story 5

- [x] T032 [P] [US5] `BrokenRecordTest` в `src/test/java/com/example/dashboard/BrokenRecordTest.java`: 1000 записей, 5 с отрицательной ценой → 995 в витрине, 5 в `etl_error`, прогон `SUCCESS` (SC-5). Тест падает до реализации.
- [x] T033 [P] [US5] `ReconciliationTest` в `src/test/java/com/example/dashboard/ReconciliationTest.java`: удалить 100 записей из витрины → сверка возвращает `mismatch = 100` (SC-4). Тест падает до реализации.

### Implementation for User Story 5

- [x] T034 [US5] Валидация записей и `ErrorRepo` в `src/main/java/com/example/dashboard/mart/`: битые записи (обязательные поля, неотрицательная цена) → `etl_error` с причиной, исключение из upsert, батч не прерывается (FR-6) — доработка `MartLoader.loadBatch`
- [x] T035 [US5] `Reconciler.reconcile()` в `src/main/java/com/example/dashboard/reconcile/Reconciler.java`: сравнение `count(*)` источника и витрины, результат `ReconcileReport{sourceCount, martCount, mismatch, details}` (research R6)
- [x] T036 [US5] Эндпоинт `POST /api/etl/reconcile` в `src/main/java/com/example/dashboard/api/EtlController.java` + `@Scheduled(cron ...)` ночная сверка 03:00 в `src/main/java/com/example/dashboard/reconcile/ScheduledReconcileRunner.java`
- [x] T037 [US5] Прогнать `src/test/java/com/example/dashboard/BrokenRecordTest.java` и `src/test/java/com/example/dashboard/ReconciliationTest.java` до зелёного состояния

**Checkpoint**: US5 работает; аудит, валидация и сверка завершены.

---

## Phase 8: Polish & Cross-Cutting

- [x] T038 [P] Скрипт наполнения `scripts/seed_source.sql`: 1 млн строк через `generate_series` (SC-1, data-model.md)
- [x] T039 `PerformanceTest` с JUnit-тегом `perf` в `src/test/java/com/example/dashboard/PerformanceTest.java` + Gradle-task `perfTest` в `build.gradle`: seed 1 млн строк, полный прогон, замер времени против бюджета ночного окна (SC-1)
- [x] T040 [P] Обновить `README.md`: запуск окружения, прогоны, тесты (по quickstart.md)
- [x] T041 `./gradlew spotlessApply` и полный зелёный билд `./gradlew build`
- [x] T042 Сквозная проверка всех сценариев `specs/001-catalog-sync-pipeline/quickstart.md` (1–6)

---

## Dependencies (порядок завершения историй)

```text
Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1, MVP)
                                             ├─→ Phase 4 (US2: recovery поверх pipeline)
                                             ├─→ Phase 5 (US3: guard в upsert)
                                             ├─→ Phase 6 (US4: REST/schedule поверх pipeline)
                                             └─→ Phase 7 (US5: валидация в loadBatch, сверка)
Phase 8 (Polish) — после всех историй
```

US2–US5 зависят от US1 (ядро пайплайна); между собой независимы — после Phase 3 могут
выполняться в любом порядке, кроме US5, который дорабатывает `MartLoader` (возможен
конфликт с US3 по файлам `MartRepo.java`/`MartLoader.java` — выполнять
последовательно).

## Parallel Execution Examples

- Phase 1: T002, T003, T004 — параллельно (разные файлы), после T001.
- Phase 2: T006, T008, T009, T010 — параллельно; T005 независим; T007 после миграций.
- Phase 3: T012–T016 (репозитории и SourceReader, разные файлы) — параллельно; T017–T018 —
  последовательно.
- Phase 7: T032 и T033 (тесты) — параллельно; T035 и T036 — параллельно после T034.
- Phase 8: T038 и T040 — параллельно.

## Independent Test Criteria (по историям)

- **US1**: новые/изменённые записи перенесены, пустой запуск — 0 изменений (SC-2).
- **US2**: прерывание между батчами + рестарт → 0 потерь, 0 дублей (SC-3).
- **US3**: старая версия не перезаписывает новую; повтор — no-op.
- **US4**: расписание срабатывает, ручной запуск возвращает `runId`, конкурентный — 409.
- **US5**: 995/5 по битым записям (SC-5); сверка видит 100 удалённых (SC-4).

## Implementation Strategy

1. **MVP = Phase 1 + 2 + 3 (US1)** — уже даёт рабочую репликацию с чекпоинтом.
2. Инкременты: US2 (надёжность) → US3 (защита версий) → US4 (автономность) → US5 (аудит
   и сверка).
3. Polish: производительность (SC-1) и документация — в конце, когда поведение стабильно.
4. Каждая фаза завершается зелёным `./gradlew build` и `spotlessCheck`.
