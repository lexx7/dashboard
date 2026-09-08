# dashboard

ETL-пайплайн «Источник → Витрина товаров»: инкрементальная синхронизация каталога
из db-source в db-mart по курсору `(updated_at, sku)` батчами, с идемпотентным
upsert'ом, чекпоинтом, аудитом сырых данных и реконсиляцией. Спецификация и план —
в `specs/001-catalog-sync-pipeline/`.

Стек: Java 25, Spring Boot 4.x, Gradle, PostgreSQL 17 (два контейнера), Flyway,
JUnit 5 + Testcontainers, Spotless.

## Окружение

```bash
docker compose up -d        # db-source (localhost:5433), db-mart (localhost:5434)
```

Flyway накатывает миграции при старте приложения: `db/migration/source` на источник,
`db/migration/mart` на витрину.

Тестовые данные (1 млн строк для perf-сценария):

```bash
psql -h localhost -p 5433 -U etl -d source -f scripts/seed_source.sql
```

## Сборка и тесты

```bash
./gradlew build             # компиляция + Testcontainers-тесты (требуется Docker)
./gradlew spotlessCheck     # формат (fix: ./gradlew spotlessApply)
./gradlew perfTest          # perf-тест SC-1: 1 млн строк, полный прогон (тег perf)
```

## Запуск

```bash
./gradlew bootRun
```

Пайплайн опрашивает источник каждые 60 секунд (`etl.poll-interval`), ночная сверка —
в 03:00 (`etl.reconcile-cron`).

## Управление (REST)

```bash
curl -X POST localhost:8080/api/etl/run        # { "runId": 1 } — ручной прогон (409, если уже идёт)
curl localhost:8080/api/etl/run/1              # статус и счётчики прогона (404, если нет)
curl -X POST localhost:8080/api/etl/reconcile  # { sourceCount, martCount, mismatch, details }
```

## Настройки (`src/main/resources/application.yaml`)

| Ключ | Значение по умолчанию | Назначение |
|---|---|---|
| `etl.batch-size` | 1000 | размер батча |
| `etl.lag-window` | PT30S | окно отставания курсора |
| `etl.poll-interval` | PT60S | интервал опроса источника |
| `etl.reconcile-cron` | `0 0 3 * * *` | расписание ночной сверки |
| `etl.scheduling.enabled` | true | включить/выключить планировщики |
