# NeedU 로컬 부하 테스트

로컬에서 병목 위치를 찾기 위한 환경이다. 절대적인 처리 가능 RPS는 동일한 구성을 운영 사양의 Linux/EC2에서 다시 측정한다.

실제 준비·시작·k6 실행·종료 절차는 [RUNBOOK.md](RUNBOOK.md)를 따른다.

## 1. 준비

필요 도구:

- Docker Desktop
- k6 (`brew install k6`)
- `curl`

```bash
cd loadtest
cp .env.example .env
set -a
source .env
set +a
```

`.env`의 `JWT_SECRET`은 로컬 부하 테스트에서만 사용한다. 운영 비밀키를 입력하지 않는다.
앱 JVM은 컨테이너 메모리의 75%를 최대 힙으로 사용하며 `APP_MAX_RAM_PERCENTAGE`로 조정할 수 있다.

## 2. 실행

```bash
docker compose up -d --build
docker compose ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/actuator/prometheus >/dev/null
```

Linux/EC2에서 컨테이너 CPU·메모리까지 수집할 때만 cAdvisor 프로필을 추가한다.

```bash
docker compose --profile cadvisor up -d --build
```

초기 시드는 새 MySQL volume에서만 실행된다. 기본 상한은 조회 VU 200명, AI VU 200명이다.

| 도구 | 주소 |
| --- | --- |
| Grafana | <http://localhost:3001> (`admin` / `.env`의 비밀번호) |
| Prometheus | <http://localhost:9090> |
| Prometheus targets | <http://localhost:9090/targets> |
| cAdvisor | <http://localhost:8081> |
| MySQL | `localhost:3307`, `needu` / `needu` |

## 3. k6 실행

모든 k6 실행 전에 `loadtest/.env`를 export한다.

```bash
set -a
source .env
set +a
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS='p(50),p(95),p(99)'
```

### Smoke test

```bash
SCENARIO=read READ_VUS=1 READ_RAMP_UP=1s READ_HOLD=1s READ_RAMP_DOWN=1s \
k6 run -o experimental-prometheus-rw k6/scenarios.js

SCENARIO=ai AI_VUS=1 \
k6 run -o experimental-prometheus-rw k6/scenarios.js
```

AI smoke test도 사용자의 24시간 cooldown을 만든다. 반복 실행하려면 아래의 volume 초기화를 먼저 수행한다.

### 조회 부하

한 iteration은 친구·개인 추천·선물 추천의 1·2페이지, 총 6개 HTTP 요청을 보낸다.

```bash
READ_VUS=20 READ_HOLD=2m \
SCENARIO=read k6 run -o experimental-prometheus-rw k6/scenarios.js
```

`READ_VUS`를 20 → 50 → 100 순서로 올린다. 노트북 전체 CPU가 포화되면 해당 구간은 결과에서 제외한다.

### AI 동시성 부하

VU마다 별도 사용자로 대화 시작 → 메시지 → 분석 → 확정을 한 번 수행한다.

```bash
AI_VUS=20 AI_TURNS=2 \
SCENARIO=ai k6 run -o experimental-prometheus-rw k6/scenarios.js
```

`AI_TURNS`는 `.env`의 `AI_SERVER_MOCK_MAX_TURNS`와 같아야 한다. Fake AI의 호출당 지연은 `AI_SERVER_MOCK_LATENCY`로 변경한다.

## 4. 결과 판독

Grafana의 `NeedU / NeedU Load Test Overview`를 연다.

| 현상 | 우선 확인 |
| --- | --- |
| p95 증가 + Tomcat busy 상한 | AI 동기 대기, Tomcat thread 고갈 |
| p95 증가 + Hikari pending 증가 | DB connection pool 고갈 |
| Hikari는 여유 + MySQL running/lock 증가 | query/lock 병목 |
| App CPU 상한 + thread/connection 여유 | application CPU 병목 |
| dropped iterations 증가 | k6 VU 부족 또는 SUT 응답 지연 |

로컬 합격 기준은 HTTP 실패율 1% 미만, check 실패 0, dropped iteration 0이다. p95와 RPS는 합격선이 아니라 비교 값으로 기록한다.

### 결과 기록 템플릿

```text
Git SHA:
시나리오 / VU:
앱 CPU / Memory:
MySQL CPU / Memory:
Tomcat threads / Hikari pool:
Fake AI latency / turns:
RPS / p50 / p95 / p99:
HTTP 실패율 / dropped iterations:
노트북 전체 CPU:
병목 후보:
비고:
```

## 5. 종료와 초기화

컨테이너만 종료하고 DB·Prometheus·Grafana 데이터를 유지한다.

```bash
docker compose down
```

다음 명령은 **로컬 부하 테스트 DB와 수집한 모든 메트릭을 삭제**한다. AI 시나리오 재실행 또는 시드 초기화가 필요할 때만 사용한다.

```bash
docker compose down -v
docker compose up -d --build
```

## 6. 문제 해결

- `/actuator/prometheus` 401: 앱이 `loadtest` 프로필로 실행 중인지 확인한다.
- Grafana 패널이 비음: Prometheus `/targets`에서 `needu-app`, `needu-mysql`을 확인한다. k6 패널은 k6를 한 번 실행한 후에 생긴다.
- AI 200 대신 cooldown 응답: `docker compose down -v`로 전용 DB를 초기화한다.
- next cursor check 실패: 시드가 완전히 적용됐는지 확인하고, 200 VU를 넘겼다면 `mysql/02-seed.sql`의 고정 개수를 늘린다.
- macOS에서 cAdvisor 패널이 비음: 알려진 Docker Desktop 제약이다. 기본 실행에서는 cAdvisor 프로필을 끄고 Micrometer CPU/JVM 메트릭과 `docker stats`를 사용한다.
- 노트북 CPU 포화: VU를 낮추거나 동일 Compose를 EC2에 올리고 k6만 로컬에서 실행한다. 이때 서버 처리 시간은 Spring `http.server.requests`를 기준으로 판단한다.
