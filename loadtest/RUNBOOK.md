# NeedU 로컬 부하 테스트 실행 가이드

## 1. 최초 준비

Docker Desktop과 k6가 필요하다.

```bash
brew install k6
cd loadtest
cp .env.example .env
set -a
source .env
set +a
```

`.env`에는 로컬 테스트 값만 사용하고 운영 비밀키를 입력하지 않는다.

## 2. 환경 시작

```bash
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/prometheus >/dev/null
```

Linux/EC2에서 cAdvisor까지 실행하려면 다음 명령을 사용한다. macOS에서는 기본 실행과 `docker stats`를 사용한다.

```bash
docker compose --profile cadvisor up -d --build
```

접속 주소:

- Grafana: <http://localhost:3001> (`admin` / `.env`의 `GRAFANA_ADMIN_PASSWORD`)
- Prometheus: <http://localhost:9090>
- Prometheus targets: <http://localhost:9090/targets>
- cAdvisor: <http://localhost:8081>
- MySQL: `localhost:3307`, `needu` / `needu`

## 3. k6 공통 설정

새 터미널을 열었다면 환경변수를 다시 불러온다.

```bash
cd loadtest
set -a
source .env
set +a
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99)'
```

## 4. Smoke test

조회 시나리오:

```bash
SCENARIO=read READ_VUS=1 READ_RAMP_UP=1s READ_HOLD=1s READ_RAMP_DOWN=1s \
k6 run -o experimental-prometheus-rw k6/scenarios.js
```

AI 전체 흐름:

```bash
SCENARIO=ai AI_VUS=1 \
k6 run -o experimental-prometheus-rw k6/scenarios.js
```

HTTP 실패율 1% 미만, check 실패 0, dropped iteration 0인지 확인한다. AI 실행은 cooldown 데이터를 만들기 때문에 반복 전 `docker compose down -v`로 초기화한다.

## 5. 단계적 부하 실행

조회 부하는 낮은 VU부터 올린다.

```bash
READ_VUS=20 READ_HOLD=2m \
SCENARIO=read k6 run -o experimental-prometheus-rw k6/scenarios.js
```

`READ_VUS`를 20 → 50 → 100 순서로 올린다. 노트북 전체 CPU가 포화된 구간은 결과에서 제외한다.

AI 동시성 부하:

```bash
AI_VUS=20 AI_TURNS=2 \
SCENARIO=ai k6 run -o experimental-prometheus-rw k6/scenarios.js
```

`AI_TURNS`는 `.env`의 `AI_SERVER_MOCK_MAX_TURNS`와 같아야 하고, `AI_VUS`는 시드 상한인 200을 넘기지 않는다.

## 6. 종료와 초기화

데이터를 유지하고 컨테이너만 종료한다.

```bash
docker compose down
```

다음 명령은 테스트 DB, AI cooldown, Prometheus 메트릭, Grafana 데이터를 모두 삭제한다.

```bash
docker compose down -v
```

초기 상태로 다시 시작한다.

```bash
docker compose up -d --build
```

## 7. 빠른 문제 해결

- Prometheus가 401이면 앱의 `SPRING_PROFILES_ACTIVE=loadtest`를 확인한다.
- Grafana가 비어 있으면 <http://localhost:9090/targets>에서 `needu-app`, `needu-mysql`이 `UP`인지 확인한다.
- AI cooldown 오류가 나면 `docker compose down -v` 후 다시 시작한다.
- cursor check가 실패하면 MySQL volume을 초기화하고, 200 VU를 넘겨야 한다면 `mysql/02-seed.sql`의 시드 개수를 늘린다.
- macOS에서 cAdvisor 데이터가 없으면 `docker stats`와 JVM Micrometer 패널을 사용한다.
