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
    -- AI 대화 API는 온보딩을 마친 사용자만 쓸 수 있다 => AI 시나리오 사용자(201~400)도 온보딩 완료, 취향 분석만 전
    TRUE,
    number <= 200,
    'ACTIVE',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
FROM sequence;

-- AI 대화 시작·메시지 전송은 AI 대화 정보 활용 동의를 확인한다 => 필수 동의 4종을 현재 버전(ConsentType.version)으로 동의
INSERT INTO user_consents (user_id, consent_type, version, agreed, created_at)
WITH RECURSIVE sequence AS (
    SELECT 1 AS number
    UNION ALL
    SELECT number + 1 FROM sequence WHERE number < 400
)
SELECT
    sequence.number,
    consent.consent_type,
    '1.0',
    TRUE,
    UTC_TIMESTAMP(6)
FROM sequence
CROSS JOIN (
    SELECT 'PRIVACY_COLLECTION' AS consent_type
    UNION ALL SELECT 'AI_CONVERSATION'
    UNION ALL SELECT 'FRIEND_TASTE_SHARING'
    UNION ALL SELECT 'PRODUCT_ACTIVITY'
) AS consent;

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
    ELT(MOD(number, 10) + 1, 'VOUCHER', 'LIVING', 'BEAUTY', 'FASHION', 'FOOD',
        'DIGITAL', 'HEALTH', 'LUXURY', 'BOOKS_TICKETS', 'SPORTS'),
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
