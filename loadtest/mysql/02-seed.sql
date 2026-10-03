USE needu;

-- ponytail: fixed local seed sizes; parameterize only when tests need more than 200 VUs.
INSERT INTO users (
    id, external_id, nickname, profile_image_url, gender, birth_date,
    onboarding_completed, taste_analysis_completed, status,
    created_at, updated_at
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM sequence WHERE number < 400
)
SELECT
    number,
    1000000 + number,
    CONCAT('loadtest-user-', LPAD(number, 3, '0')),
    NULL,
    IF(MOD(number, 2) = 0, 'FEMALE', 'MALE'),
    DATE_ADD('1990-01-01', INTERVAL MOD(number * 17, 3650) DAY),
    number <= 200,
    number <= 200,
    IF(number <= 200, 'ACTIVE', 'ONBOARDING'),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM sequence;

INSERT INTO friends (owner_user_id, friend_user_id, is_favorite)
WITH RECURSIVE owners AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM owners WHERE number < 200
), offsets AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM offsets WHERE number < 50
)
SELECT
    owners.number,
    MOD(owners.number + offsets.number - 1, 200) + 1,
    MOD(offsets.number, 10) = 0
FROM owners
CROSS JOIN offsets;

INSERT INTO products (
    id, platform_type, external_id, name, category, description,
    price, image_url, purchase_url, status, created_at, updated_at
)
WITH RECURSIVE sequence AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM sequence WHERE number < 1000
)
SELECT
    number,
    'COUPANG',
    CASE number
        WHEN 1 THEN '8255331367'
        WHEN 2 THEN '6927419268'
        ELSE CONCAT('loadtest-', number)
    END,
    CONCAT('loadtest-product-', LPAD(number, 4, '0')),
    CONCAT('category-', MOD(number, 10)),
    'Local load test product',
    1000 + number * 100,
    NULL,
    CONCAT('https://example.com/products/', number),
    'ACTIVE',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM sequence;

INSERT INTO personal_recommendations (
    user_id, product_id, score, reason, created_at, updated_at
)
WITH RECURSIVE users AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM users WHERE number < 200
), products AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM products WHERE number < 200
)
SELECT
    users.number,
    products.number,
    CAST((1000 - products.number) / 1000 AS DECIMAL(8, 6)),
    'Local load test recommendation',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM users
CROSS JOIN products;

INSERT INTO gift_recommendations (
    user_id, product_id, score, reason, taste_keywords, created_at, updated_at
)
WITH RECURSIVE users AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM users WHERE number < 200
), products AS (
    SELECT 201 AS number
    UNION ALL
    SELECT number + 1 FROM products WHERE number < 400
)
SELECT
    users.number,
    products.number,
    CAST((1200 - products.number) / 1000 AS DECIMAL(8, 6)),
    'Local load test gift recommendation',
    JSON_ARRAY('loadtest'),
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM users
CROSS JOIN products;
