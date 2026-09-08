# PROCESS.md — журнал итераций ETL-пайплайна

- 2026-09-08 | Phase 1 (T001–T004, Setup) | OK | `./gradlew spotlessApply build` — зелёный.
  Нюансы: настройки T004 влиты в существующий `application.yaml` (дубль `application.yml`
  не создавался); Flyway отключён до Phase 2 (миграции per-datasource). Замечание:
  `.kimi-code/AGENTS.md` ссылается на Maven/`./mvnw` — проект на Gradle (`./gradlew`),
  команды Maven неприменимы.
