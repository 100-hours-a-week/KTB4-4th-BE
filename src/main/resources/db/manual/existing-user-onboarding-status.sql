-- 기존 회원은 V2 온보딩 도입 전에 이미 서비스를 이용했으므로 온보딩 완료 상태로 유지한다.
--
-- 실행 방법
--  * 자동 실행되지 않는다. 신규 회원 기본값을 바꾼 앱을 배포하기 전에 DB별로 한 번 실행한다.
--  * 상태는 변경하지 않아 BLOCKED·WITHDRAWN 회원을 ACTIVE로 복구하지 않는다.
--  * 신규 회원의 ONBOARDING 상태는 제외하므로 재실행해도 완료 처리되지 않는다.

UPDATE users
SET onboarding_completed = 1
WHERE status IN ('ACTIVE', 'BLOCKED', 'WITHDRAWN')
  AND onboarding_completed = 0;

-- 확인: ACTIVE 회원은 0건이어야 한다.
SELECT status, onboarding_completed, COUNT(*) AS user_count
FROM users
WHERE status = 'ACTIVE'
  AND onboarding_completed = 0
GROUP BY status, onboarding_completed;
