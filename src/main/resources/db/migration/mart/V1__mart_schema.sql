CREATE TABLE etl_run (
    id           BIGSERIAL PRIMARY KEY,
    pipeline     TEXT NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    finished_at  TIMESTAMPTZ,
    status       TEXT NOT NULL,
    rows_staged  BIGINT DEFAULT 0,
    rows_loaded  BIGINT DEFAULT 0,
    rows_failed  BIGINT DEFAULT 0
);

CREATE TABLE staging_raw (
    id         BIGSERIAL PRIMARY KEY,
    run_id     BIGINT NOT NULL REFERENCES etl_run (id),
    sku        TEXT NOT NULL,
    payload    JSONB NOT NULL,
    staged_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_staging_run ON staging_raw (run_id);

CREATE TABLE products_mart (
    sku        TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    price      NUMERIC(12, 2) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE etl_checkpoint (
    pipeline    TEXT PRIMARY KEY,
    last_cursor TIMESTAMPTZ NOT NULL,
    last_sku    TEXT NOT NULL DEFAULT ''
);

CREATE TABLE etl_error (
    id      BIGSERIAL PRIMARY KEY,
    run_id  BIGINT NOT NULL REFERENCES etl_run (id),
    sku     TEXT,
    payload JSONB,
    reason  TEXT NOT NULL
);
