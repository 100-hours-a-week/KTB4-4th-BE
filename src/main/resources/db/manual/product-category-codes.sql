-- products.category를 ProductCategory 코드(대문자)로 바꾼다
--
-- 실행 방법
--  * 자동 실행되지 않는다. 배포 DB와 로컬 DB에서 각각 한 번 직접 실행한다
--  * 앱이 category를 Enum으로 읽으므로 이 스크립트를 먼저 실행한 뒤 새 버전을 배포한다
--    코드가 아닌 값이 남아 있으면 그 상품이 들어간 추천 목록 조회가 실패한다
--  * 이미 바뀐 행은 조건에서 빠지거나 같은 값으로 바뀌어, 다시 실행해도 결과가 같다
--  * 실행 전 백업: mysqldump -h 127.0.0.1 -P 13306 -u <DB_USERNAME> -p needu products > products_backup.sql

-- 클라이언트 기본 문자셋이 utf8mb4가 아니면 한글 분류명이 깨져 아무 행도 바뀌지 않는다
SET NAMES utf8mb4;

-- 1. 기존 쿠팡·11번가·홈플러스 한글 분류명 → 코드
UPDATE products
SET category = CASE category
    WHEN '패션의류/잡화' THEN 'FASHION'
    WHEN '뷰티' THEN 'BEAUTY'
    WHEN '식품' THEN 'FOOD'
    WHEN '헬스/건강식품' THEN 'HEALTH'
    WHEN '주방용품' THEN 'LIVING'
    WHEN '생활용품' THEN 'LIVING'
    WHEN '홈인테리어' THEN 'LIVING'
    WHEN '가전디지털' THEN 'DIGITAL'
    WHEN '스포츠/레저' THEN 'SPORTS'
    WHEN '도서/음반/DVD' THEN 'BOOKS_TICKETS'
    WHEN '반려동물용품' THEN 'PET'
    WHEN '쌀/잡곡' THEN 'FOOD'
    WHEN '과일' THEN 'FOOD'
    WHEN '채소' THEN 'FOOD'
    WHEN '정육/계란' THEN 'FOOD'
    WHEN '수산물' THEN 'FOOD'
    WHEN '우유/유제품' THEN 'FOOD'
    WHEN '냉장/냉동/간편식' THEN 'FOOD'
    WHEN '라면/면류' THEN 'FOOD'
    WHEN '생수/음료' THEN 'FOOD'
    WHEN '커피/차' THEN 'FOOD'
    WHEN '과자/간식' THEN 'FOOD'
    WHEN '세제/청소' THEN 'LIVING'
    WHEN '화장지/위생' THEN 'LIVING'
    WHEN '반려동물' THEN 'PET'
END
WHERE category IN (
    '패션의류/잡화',
    '뷰티',
    '식품',
    '헬스/건강식품',
    '주방용품',
    '생활용품',
    '홈인테리어',
    '가전디지털',
    '스포츠/레저',
    '도서/음반/DVD',
    '반려동물용품',
    '쌀/잡곡',
    '과일',
    '채소',
    '정육/계란',
    '수산물',
    '우유/유제품',
    '냉장/냉동/간편식',
    '라면/면류',
    '생수/음료',
    '커피/차',
    '과자/간식',
    '세제/청소',
    '화장지/위생',
    '반려동물'
);

-- 2. 새 카테고리 체계에 대응하는 분류가 없는 상품은 카테고리 탭에서 빼고 '전체'에서만 보이게 한다
UPDATE products
SET category = NULL
WHERE category IN (
    '자동차용품',
    '출산/유아동',
    '완구/취미',
    '문구/오피스'
);

-- 3. 소문자 코드(voucher 등)로 적재된 값 → 대문자. 컬럼 콜레이션이 대소문자를 구분하지 않아 bin으로 비교한다
UPDATE products
SET category = UPPER(category)
WHERE category COLLATE utf8mb4_bin IN ('voucher', 'living', 'beauty', 'fashion', 'food', 'digital', 'health', 'luxury', 'books_tickets', 'sports', 'pet');

-- 확인: 결과가 0건이어야 한다. 남은 값이 있으면 위 매핑에 추가하고 다시 실행한다
SELECT category, COUNT(*) AS product_count
FROM products
WHERE category IS NOT NULL
  AND category COLLATE utf8mb4_bin NOT IN ('VOUCHER', 'LIVING', 'BEAUTY', 'FASHION', 'FOOD', 'DIGITAL', 'HEALTH', 'LUXURY', 'BOOKS_TICKETS', 'SPORTS', 'PET')
GROUP BY category;
