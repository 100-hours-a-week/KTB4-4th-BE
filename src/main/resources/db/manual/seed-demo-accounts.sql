-- 외부 평가용 테스트 계정 5개와 친구 관계 시드 (#406)
-- 계정 상태
-- * 온보딩 미완(status = ONBOARDING, onboarding_completed = 0)
--   평가자가 온보딩부터 진행하므로 동의·취향 프로필·추천 상품은 넣지 않는다.
-- * 취향분석 미완(taste_analysis_completed = 0)
-- * 친구 연동 완료(kakao_friend_synced_at 설정)
--   친구 목록은 팀 계정 6개와 나머지 테스트 계정 4개다.
-- 사용 방법
-- * 카카오 계정이 없으므로 external_id는 NULL이고, 로그인은 POST /api/v1/auth/demo/login으로 한다.
-- * 실행한 뒤 마지막 SELECT로 나온 user_id를 AUTH_DEMOLOGIN_ACCOUNTS_{0..4}_USERID 환경변수에 넣는다.
-- * 다시 실행해도 사용자와 친구 행을 중복으로 만들지 않는다.
--   이미 온보딩을 진행한 계정의 상태는 되돌리지 않는다.
-- * 팀 계정은 카카오 회원번호(external_id)로 찾는다. DB에 없거나 탈퇴한 회원은 건너뛴다.

START TRANSACTION;

-- 1. 테스트 사용자 -------------------------------------------------------------

INSERT INTO users (
    external_id, nickname, profile_image_url, gender, birth_date,
    onboarding_completed, taste_analysis_completed, status,
    kakao_friend_synced_at, created_at, updated_at
)
SELECT NULL, demo.nickname, NULL, 'NONE', NULL,
       0, 0, 'ONBOARDING',
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT '니듀 평가자1' AS nickname
    UNION ALL SELECT '니듀 평가자2'
    UNION ALL SELECT '니듀 평가자3'
    UNION ALL SELECT '니듀 평가자4'
    UNION ALL SELECT '니듀 평가자5'
) demo
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    WHERE u.nickname = demo.nickname
      AND u.external_id IS NULL
      AND u.status <> 'WITHDRAWN'
);

-- 이후 문장에서 함께 쓰려고 테스트 계정 ID를 임시 테이블에 모은다.
DROP TEMPORARY TABLE IF EXISTS demo_users;
CREATE TEMPORARY TABLE demo_users (id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO demo_users (id)
SELECT MIN(id)
FROM users
WHERE nickname IN ('니듀 평가자1', '니듀 평가자2', '니듀 평가자3', '니듀 평가자4', '니듀 평가자5')
  AND external_id IS NULL
  AND status <> 'WITHDRAWN'
GROUP BY nickname;

-- MySQL은 한 쿼리에서 같은 임시 테이블을 두 번 열 수 없어 친구 조합용 사본을 둔다.
DROP TEMPORARY TABLE IF EXISTS demo_users_copy;
CREATE TEMPORARY TABLE demo_users_copy (id BIGINT NOT NULL PRIMARY KEY);
INSERT INTO demo_users_copy (id) SELECT id FROM demo_users;

UPDATE users
SET kakao_friend_synced_at = COALESCE(kakao_friend_synced_at, UTC_TIMESTAMP(6)),
    updated_at = UTC_TIMESTAMP(6)
WHERE id IN (SELECT id FROM demo_users);

-- 2. 친구 관계 (양방향) ---------------------------------------------------------
-- uk_friends_owner_friend 제약이 중복 행을 막는다.

-- 테스트 계정끼리 연결한다. 계정마다 나머지 테스트 계정 4개가 친구가 된다.
INSERT IGNORE INTO friends (owner_user_id, friend_user_id, is_favorite)
SELECT a.id, b.id, 0
FROM demo_users a
JOIN demo_users_copy b ON a.id <> b.id;

-- 테스트 계정과 팀 계정 6개를 서로 친구로 연결한다.
INSERT IGNORE INTO friends (owner_user_id, friend_user_id, is_favorite)
SELECT d.id, r.id, 0
FROM demo_users d
JOIN users r
  ON r.external_id IN (5102468789, 5094461511, 5100000782, 5093396570, 5103309392, 5108847125)
 AND r.status <> 'WITHDRAWN';

INSERT IGNORE INTO friends (owner_user_id, friend_user_id, is_favorite)
SELECT r.id, d.id, 0
FROM demo_users d
JOIN users r
  ON r.external_id IN (5102468789, 5094461511, 5100000782, 5093396570, 5103309392, 5108847125)
 AND r.status <> 'WITHDRAWN';

COMMIT;

-- 3. 결과 확인 ----------------------------------------------------------------
-- 계정마다 demo_friend_count가 4, team_friend_count가 6이어야 한다.

SELECT u.id AS user_id,
       u.nickname,
       u.status,
       u.onboarding_completed,
       u.taste_analysis_completed,
       u.kakao_friend_synced_at,
       (SELECT COUNT(*) FROM friends f
        WHERE f.owner_user_id = u.id AND f.friend_user_id IN (SELECT id FROM demo_users_copy))
           AS demo_friend_count,
       (SELECT COUNT(*) FROM friends f JOIN users r ON r.id = f.friend_user_id
        WHERE f.owner_user_id = u.id
          AND r.external_id IN (5102468789, 5094461511, 5100000782, 5093396570, 5103309392, 5108847125))
           AS team_friend_count
FROM users u
WHERE u.id IN (SELECT id FROM demo_users)
ORDER BY u.id;

DROP TEMPORARY TABLE IF EXISTS demo_users;
DROP TEMPORARY TABLE IF EXISTS demo_users_copy;
