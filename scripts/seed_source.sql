-- Наполнение db-source.products тестовым каталогом (SC-1: 1 млн строк).
-- Применение: psql -h localhost -p 5433 -U etl -d source -f scripts/seed_source.sql

TRUNCATE products;

INSERT INTO products (sku, name, price, updated_at)
SELECT 'SKU-' || g,
       'Product ' || g,
       (random() * 1000)::numeric(12, 2),
       now() - interval '1 minute'
FROM generate_series(1, 1000000) AS g;
