# Contract: REST API ручного управления пайплайном

Внутренний API для эксплуатации и тестов (FR-7, FR-8). Доступ ограничен сетевым
периметром, авторизация не требуется. Формат — JSON, даты ISO 8601 UTC.
Соответствует plan.md §5.

## POST /api/etl/run

Ручной запуск прогона пайплайна (нужен для crash-теста SC-3 и проверок вне расписания).

**Ответ 200**:

```json
{ "runId": 123 }
```

**Ошибки**: 409 — прогон уже выполняется (`etl_run.status = RUNNING`, один инстанс
пайплайна).

## GET /api/etl/run/{id}

Статус и счётчики прогона.

**Ответ 200**:

```json
{
  "id": 123,
  "status": "RUNNING | SUCCESS | FAILED",
  "startedAt": "2026-09-08T05:58:00Z",
  "finishedAt": null,
  "rowsStaged": 5000,
  "rowsLoaded": 4995,
  "rowsFailed": 5
}
```

**Ошибки**: 404 — прогон не найден.

## POST /api/etl/reconcile

Запуск сверки источника и витрины (по количеству записей, research R6).

**Ответ 200**:

```json
{
  "sourceCount": 1000000,
  "martCount": 999900,
  "mismatch": 100,
  "details": "products: source=1000000, mart=999900"
}
```

`mismatch = 0` — витрина соответствует источнику (SC-4).

## Внутренние интерфейсы (Java)

Контракты компонентов внутри приложения (plan.md §5):

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

Правила: `SourceReader` — read-only к источнику, транзакция источника не держится;
`MartLoader.loadBatch` — одна транзакция `db-mart` на батч (FR-2); `Reconciler` не
блокирует работающий прогон.

## Гарантии контракта

- Поля и типы фиксированы; ломающие изменения требуют обновления этого документа и тестов
  в том же change set (конституция, принцип V).
- Эндпоинты не выполняют тяжёлых запросов к источнику, кроме самого прогона/сверки.
