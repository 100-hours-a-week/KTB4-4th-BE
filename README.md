# KTB4-4th-BE

## 카카오 OAuth 설정

카카오디벨로퍼스 REST API 키에 `http://localhost:8080/api/v1/auth/kakao/callback`을 리다이렉트 URI로 등록합니다. 실행 환경에서 다음 값을 설정합니다.

- `KAKAO_REST_API_KEY`: REST API 키
- `KAKAO_CLIENT_SECRET`: REST API 키의 클라이언트 시크릿
- `KAKAO_REDIRECT_URI`: 등록한 리다이렉트 URI와 정확히 같은 값

DB 연결은 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 설정합니다. HTTPS 운영 환경에서는 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`로 설정합니다.

브라우저에서 `GET /api/v1/auth/kakao/authorize`로 이동하면 카카오 로그인 화면으로 리다이렉트됩니다. 성공 후 콜백은 회원 정보 JSON을 반환하며 NeedU 인증 토큰은 발급하지 않습니다. 기존 MySQL `users` 테이블에는 [`db/unique_users_external_id.sql`](db/unique_users_external_id.sql)을 적용해야 합니다. 적용 전 `external_id` 중복 행을 정리하세요.
