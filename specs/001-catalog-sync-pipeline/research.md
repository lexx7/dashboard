# Research: ETL-пайплайн «Источник → Витрина товаров»

Решения соответствуют plan.md (после его переработки владельцем). Открытых
NEEDS CLARIFICATION нет.

## R1. Стек и оркестрация окружения

- **Decision**: Java 21, Spring Boot 3.3, Maven (mvnw), Spotless для формата; окружение —
  Docker Compose с двумя контейнерами PostgreSQL 16 (`db-source`, `db-mart`).
- **Rationale**: Зафиксировано в plan.md §2. Два отдельных контейнера честно имитируют две
  разные системы (изоляция «чужой» БД, отдельные наборы миграций).
- **Alternatives considered**: две схемы в одной БД (проще, но нет честной изоляции);
  Gradle/Java 25/Spring Boot 4.1 текущего репозитория (см. «Открытый вопрос» ниже).

## R2. Курсорное чтение источника

- **Decision**: Выборка `WHERE (updated_at, sku) > (:cursor, :lastSku) ORDER BY
  updated_at, sku LIMIT :batch` с миллисекундной гранулярностью курсора; окно отставания:
  читаем только записи с `updated_at < now() - interval '30 seconds'`.
- **Rationale**: Составной курсор детерминированно разрешает одинаковые метки времени
  (граничный случай спеки); окно отставания устраняет пропуск записей из долгих
  транзакций, закоммиченных после прохода курсора (риск из plan.md §9).
- **Alternatives considered**: CDC (Debezium) — не поднять в рамках практической задачи;
  курсор покрывает FR-1/FR-2 (таблица обоснований plan.md §2).

## R3. Атомарность батча и идемпотентность

- **Decision**: Каждый батч — одна транзакция на `db-mart`: staging-insert → валидация →
  upsert в `products_mart` → продвижение чекпоинта → статистика прогона. Upsert:
  `INSERT ... ON CONFLICT (sku) DO UPDATE ... WHERE products_mart.updated_at <
  EXCLUDED.updated_at`.
- **Rationale**: Атомарность «батч + чекпоинт» даёт resume без потерь и дублей (FR-2,
  FR-3, SC-3); условие `WHERE ... < EXCLUDED.updated_at` атомарно на уровне БД защищает
  от устаревших версий (FR-4).
- **Alternatives considered**: merge-логика в коде (две операции вместо одной, гонки);
  dedup-таблица обработанных записей (избыточна при идемпотентном upsert).

## R4. Staging-аудит и обработка битых записей

- **Decision**: Сырые записи складываются в `staging_raw` (JSONB payload, `run_id`)
  до загрузки (FR-5). Валидация (обязательные поля, неотрицательная цена) выполняется до
  upsert; битые записи пишутся в `etl_error` с причиной, батч не прерывается (FR-6).
- **Rationale**: Аудит «как есть» нужен для отладки и расследований; изоляция битых
  записей не даёт одной записи остановить весь прогон (SC-5).
- **Alternatives considered**: прерывание батча при ошибке (fail-fast) — блокирует весь
  каталог из-за единичных дефектов данных; пропуск без журналирования — теряется
  наблюдаемость.

## R5. Планировщик и ручной запуск

- **Decision**: Spring `@Scheduled` для периодических прогонов + REST-эндпоинты ручного
  запуска (plan.md §5). Конкурентные запуски исключены: один активный прогон
  (`etl_run.status = RUNNING` + advisory lock).
- **Rationale**: Скоуп — локальный прогон; Quartz/внешний cron — избыточная сложность
  (plan.md §2). Ручной запуск нужен для crash-теста SC-3.
- **Alternatives considered**: Quartz (кластерные джобы не нужны), внешний cron (теряется
  связность с состоянием приложения).

## R6. Реконсиляция по количеству

- **Decision**: Джоба сверки (cron 03:00 / ручной REST-запуск) сравнивает `count(*)`
  источника и витрины и возвращает `{ sourceCount, martCount, mismatch, details }`.
- **Rationale**: Дёшево и достаточно для контроля SC-4 на append/update-only источнике;
  компромисс зафиксирован в plan.md §9.
- **Alternatives considered**: построчная сверка по хэшам пачек — точнее, но дороже;
  отнесена в backlog (компромисс plan.md §9).

## R7. Мониторинг и логирование

- **Decision**: Таблица `etl_run` (статус, время, счётчики staged/loaded/failed) +
  структурные JSON-логи ключевых событий (FR-8).
- **Rationale**: Минимальный достаточный набор для локального скоупа; статус и счётчики
  доступны через REST (`GET /api/etl/run/{id}`).
- **Alternatives considered**: Micrometer/Prometheus — избыточны для локального прогона;
  внешний алертинг вне скоупа задачи.

## R8. Тестирование

- **Decision**: JUnit 5 + Testcontainers (два PG-контейнера); набор тестов по маппингу
  plan.md §7: `IncrementalSyncTest`, `CrashRecoveryTest` (interrupt между батчами),
  `OutOfOrderTest`, `BrokenRecordTest`, `ReconciliationTest`, `PerformanceTest`
  (отдельный профиль, seed 1 млн строк).
- **Rationale**: Каждый тест замаплен на критерий успеха (SC-1…SC-5); Testcontainers даёт
  воспроизводимые две БД.
- **Alternatives considered**: H2/in-memory — диалект и `ON CONFLICT` отличаются от PG.

## Открытый вопрос (для фазы implement, не блокирует план)

- plan.md описывает standalone Maven-проект `task7-etl-pipeline/` (Java 21, Spring Boot
  3.3), тогда как текущий репозиторий — Gradle, Java 25, Spring Boot 4.1.1. На фазе
  реализации нужно выбрать: создать подпроект `task7-etl-pipeline/` рядом с текущим
  приложением (соответствует plan.md §6) или реализовать фичу в текущем репозитории с его
  стеком. По умолчанию следуем plan.md — отдельный подпроект.
