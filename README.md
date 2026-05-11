# alt — 구독 서비스 백엔드

회원의 구독 상태(NONE / BASIC / PREMIUM)와 채널 권한을 다루며, 외부 API(csrng) 호출 결과에 따라 트랜잭션을 처리하고, 이력은 LLM(OpenAI gpt-4o-mini)으로 자연어 요약을 제공한다.

## TL;DR

- **언어/프레임워크**: Java 21 + Spring Boot 3.5.6
- **아키텍처**: Gradle 멀티모듈 헥사고날, 의존 방향을 ArchUnit으로 강제
- **진입점 분리**: `bootstrap/bootstrap-api`(현재) + 향후 `bootstrap-scheduler` / `bootstrap-worker` 등 모듈 조합으로 추가 ([ADR-0005](docs/adr/0005-bootstrap-multi-entry.md))
- **저장소**: MySQL 8 + Flyway (Testcontainers 기반 통합 테스트)
- **외부 API**: csrng / OpenAI — Resilience4j (CircuitBreaker + Retry + TimeLimiter + Bulkhead)
- **트랜잭션 전략**: Saga + Attempt 상태 머신 — csrng는 트랜잭션 밖, 모든 시도를 이력에 영구 기록
- **멱등성**: HTTP `Idempotency-Key` 헤더 필수 + Caffeine 게이트 + DB UNIQUE

## 빠른 실행 (평가용 — Docker Compose 한 방)

```bash
# (선택) LLM 요약을 실제로 검증하려면 키 주입. 없으면 summary=null 로 graceful degradation
export OPENAI_API_KEY=sk-...

# MySQL + App 한 번에 기동 — 시드 데이터(회원 010-1234-5678 의 구독 이력 5건) 자동 적재
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- **자동 검증 한 방** (구독/해지/멱등성/채널 권한/도메인 전이/에러 처리 17 케이스):
  ```bash
  ./scripts/demo.sh
  # → ALL PASS — 17 / 17
  ```
- 데모 회원 이력 즉시 조회: `curl 'http://localhost:8080/api/v1/subscriptions/history?phoneNumber=01012345678'`
- 자세한 검증 시나리오 + 에러 코드 일람: [docs/quickstart.md](docs/quickstart.md)
- 재기동 깔끔하게: `docker compose down -v` (볼륨까지 삭제)

### 로컬 IDE 개발

```bash
docker compose up -d mysql                                    # MySQL 만 컨테이너로
./gradlew :bootstrap:bootstrap-api:bootRun                    # IDE / gradle 로 앱 부팅
./gradlew test                                                # 단위 + ArchUnit + Testcontainers 통합
```

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/subscriptions/subscribe` | 구독 신청 (회원 신규 가입 포함) |
| POST | `/api/v1/subscriptions/unsubscribe` | 구독 해지 |
| GET | `/api/v1/subscriptions/history` | 이력 조회 + LLM 요약 |

POST는 `Idempotency-Key: <UUID>` 헤더 필수. 자세한 데이터 흐름은 [docs/architecture/api-flows.md](docs/architecture/api-flows.md).

## 문서

| 분류 | 문서 |
|---|---|
| **평가용 검증 시나리오** | **[docs/quickstart.md](docs/quickstart.md)** |
| 시스템 개요 | [docs/architecture/overview.md](docs/architecture/overview.md) |
| API 데이터 흐름 | [docs/architecture/api-flows.md](docs/architecture/api-flows.md) |
| 외부 API 장애 대응 | [docs/architecture/resilience.md](docs/architecture/resilience.md) |
| AWS 배포 설계 | [docs/architecture/aws-deployment.md](docs/architecture/aws-deployment.md) |
| CI/CD 흐름 | [docs/architecture/cicd.md](docs/architecture/cicd.md) |
| ADR | [docs/adr/](docs/adr/) — 헥사고날 / Saga / 도메인 순수성 / 멱등성 / bootstrap 다중 진입점 / LLM stale fallback |
| 과제 원문 | [docs/assignment.md](docs/assignment.md) |
