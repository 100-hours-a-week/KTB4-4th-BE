# KTB4-4th-BE

## 카카오 OAuth 설정

카카오디벨로퍼스 REST API 키에 FE 동일 출처 프록시의 콜백 주소를 리다이렉트 URI로 등록합니다. 로컬 FE가 3000 포트라면 `http://localhost:3000/api/v1/auth/kakao/callback`을 등록합니다. 실행 환경에서 다음 값을 설정합니다.

- `KAKAO_REST_API_KEY`: REST API 키
- `KAKAO_CLIENT_SECRET`: REST API 키의 클라이언트 시크릿
- `KAKAO_REDIRECT_URI`: 등록한 리다이렉트 URI와 정확히 같은 값
- `JWT_SECRET`: `openssl rand -base64 32`로 생성한 Base64 인코딩 비밀키 (디코딩 후 최소 32바이트)
- `AUTH_COOKIE_SECURE`: 운영 HTTPS에서는 기본값 `true`, 로컬 HTTP에서만 `false`

`.env.example`을 `.env`로 복사한 뒤 실제 FE 주소와 비밀값을 입력합니다. `JWT_SECRET`은 위 명령으로 새로 생성합니다.

카카오 로그인 성공 후 콜백은 15분 액세스 JWT와 14일 리프레시 토큰을 각각 `NEEDU_ACCESS_TOKEN`, `NEEDU_REFRESH_TOKEN` 쿠키로 내려줍니다. 두 쿠키는 `HttpOnly`, `SameSite=Lax`, `Path=/`, 운영 환경에서 `Secure`이며 호스트 전용입니다. 리프레시 토큰의 원문은 DB에 저장하지 않고 SHA-256 해시만 `auth_sessions`에 저장합니다. MySQL에 [`db/auth_sessions.sql`](db/auth_sessions.sql)을 적용해야 합니다.

보호된 API는 액세스 쿠키로 인증합니다. 만료되면 `POST /api/v1/auth/refresh`가 리프레시 쿠키를 교체하고 새 액세스 쿠키를 발급합니다. `POST /api/v1/auth/logout`은 리프레시 토큰을 폐기하고 두 쿠키를 삭제합니다. SSR FE의 동일 출처 프록시는 브라우저의 `Cookie`와 BE 응답의 모든 `Set-Cookie`를 전달해야 합니다.

로그아웃 후 이미 발급된 액세스 JWT는 만료 시점까지 서버에서 별도로 폐기되지 않습니다(최대 15분). OAuth `state` 검증에 사용하는 서블릿 세션도 현재는 인스턴스 로컬이므로 다중 인스턴스 배포 전 공유 저장소가 필요합니다.

변경 요청에는 CSRF 보호가 적용됩니다. FE는 `GET /api/v1/auth/csrf`의 `data.token`을 받아 쿠키와 함께 `X-XSRF-TOKEN` 헤더로 전송합니다. 이 GET 응답은 `HttpOnly` CSRF 쿠키도 설정합니다. 재발급·로그아웃 및 다른 POST/PUT/PATCH/DELETE 요청 모두 해당 헤더가 필요합니다. 운영 CSRF 쿠키는 `__Host-` 접두사를 사용합니다.

`KAKAO_REDIRECT_URI`는 브라우저가 접속하는 FE 동일 출처 프록시의 `/api/v1/auth/kakao/callback` 주소로 등록해야 합니다. BE 포트로 직접 콜백을 받으면 인증 쿠키가 FE 호스트에 설정되지 않아 SSR 요청에 전달되지 않습니다.

DB 연결은 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`로 설정합니다. HTTPS 운영 환경에서는 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`로 설정합니다.

브라우저에서 `GET /api/v1/auth/kakao/authorize?returnUrl=https%3A%2F%2Fexample.com%2Flogin`으로 이동하면 카카오 로그인 화면으로 리다이렉트됩니다. 성공 후 콜백은 쿠키를 설정하고 전달받은 `returnUrl`로 302 리다이렉트합니다. `returnUrl`은 절대 `http` 또는 `https` URL이어야 합니다. 기존 MySQL `users` 테이블에는 [`db/unique_users_external_id.sql`](db/unique_users_external_id.sql)을 적용해야 합니다. 적용 전 `external_id` 중복 행을 정리하세요.
