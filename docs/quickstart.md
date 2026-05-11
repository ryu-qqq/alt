# Quickstart — 평가자용 검증 가이드

`docker compose up --build` 로 기동한 환경에서 모든 평가 항목을 직접 검증할 수 있다. 자동 실행을 원하면 [`scripts/demo.sh`](../scripts/demo.sh) 한 방으로 모든 시나리오를 돌릴 수 있다.

## 사전 확인

```bash
# 헬스체크
curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}

# Swagger UI (브라우저)
open http://localhost:8080/swagger-ui.html
```

## 시드 데이터 (자동 적재됨)

기동 시 다음 데이터가 들어가 있다 (회원 1명 + 이력 5건).

| 일시 | 채널 | 동작 | from → to | 상태 |
|---|---|---|---|---|
| 2026-01-01 09:00 | 홈페이지 | SUBSCRIBE | NONE → BASIC | COMMITTED |
| 2026-02-01 10:30 | 모바일앱 | SUBSCRIBE | BASIC → PREMIUM | COMMITTED |
| 2026-03-01 14:00 | 콜센터 | UNSUBSCRIBE | PREMIUM → BASIC | **ROLLED_BACK** (csrng=0) |
| 2026-03-02 14:00 | 콜센터 | UNSUBSCRIBE | PREMIUM → BASIC | COMMITTED |
| 2026-04-01 10:00 | 이메일 | UNSUBSCRIBE | BASIC → NONE | COMMITTED |

회원 `01012345678` 최종 상태: `NONE`. 이력 조회는 COMMITTED 4건만 노출.

---

## 시나리오 1 — 구독 이력 + LLM 요약

```bash
curl -s 'http://localhost:8080/api/v1/subscriptions/history?phoneNumber=01012345678' | jq
```

**응답 (LLM 키 없음 / quota 부족 + 영속체 폴백 없음 시)**:
```json
{
  "data": {
    "history": [
      { "attemptId": 5, "channelId": 6, "channelName": "이메일",   "kind": "UNSUBSCRIBE", "fromStatus": "BASIC",   "toStatus": "NONE",    "occurredAt": "2026-04-01T10:00:01Z" },
      { "attemptId": 4, "channelId": 5, "channelName": "콜센터",   "kind": "UNSUBSCRIBE", "fromStatus": "PREMIUM", "toStatus": "BASIC",   "occurredAt": "2026-03-02T14:00:01Z" },
      { "attemptId": 2, "channelId": 2, "channelName": "모바일앱", "kind": "SUBSCRIBE",   "fromStatus": "BASIC",   "toStatus": "PREMIUM", "occurredAt": "2026-02-01T10:30:01Z" },
      { "attemptId": 1, "channelId": 1, "channelName": "홈페이지", "kind": "SUBSCRIBE",   "fromStatus": "NONE",    "toStatus": "BASIC",   "occurredAt": "2026-01-01T09:00:01Z" }
    ],
    "summary": null,
    "summaryGeneratedAt": null,
    "summaryStale": false
  }
}
```

**LLM 호출 성공 (fresh)**:
```json
"summary": "2026년 1월 1일 홈페이지로 일반 구독을 시작해 ...",
"summaryGeneratedAt": "2026-05-11T12:30:01Z",
"summaryStale": false
```

**이력 변경됐는데 LLM 재호출 실패 → 영속체 폴백 (stale)**:
```json
"summary": "2026년 1월 1일 ... (옛 이력 기반 요약)",
"summaryGeneratedAt": "2026-04-01T10:00:00Z",
"summaryStale": true
```

> 정책 — 응답 우선순위 fresh > stale fallback > null.
> 클라이언트는 `summaryStale=true` 일 때 "최근 요약이 아닐 수 있음" 안내 가능.
> 자세한 근거는 [ADR-0006](adr/0006-llm-summary-stale-fallback.md).

---

## 시나리오 2 — 멱등성 (Idempotency-Key)

```bash
KEY=$(uuidgen)

# 1차 — 정상
curl -s -X POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"phoneNumber":"01077776666","channelId":1,"targetStatus":"BASIC"}'
# → {"data":{"attemptId":N,"status":"COMMITTED|ROLLED_BACK|FAILED","currentStatus":"...","failureReason":null|"..."}}

# 2차 — 같은 키 → 409
curl -s -X POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"phoneNumber":"01077776666","channelId":1,"targetStatus":"BASIC"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 409, code = SUB-201 (IDEMPOTENCY_CONFLICT)
```

키 누락 시:
```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01077776666","channelId":1,"targetStatus":"BASIC"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 400, code = MISSING_HEADER
```

---

## 시나리오 3 — 채널 권한 (도메인 정책)

콜센터(id=5)는 `UNSUBSCRIBE_ONLY`:

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01055554444","channelId":5,"targetStatus":"BASIC"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 403, code = CHN-002 ("해당 채널에서는 구독할 수 없습니다")
```

---

## 시나리오 4 — 잘못된 상태 전이

`NONE → BASIC` 해지 시도 (전이 자체가 unsubscribe 의미상 불가):

```bash
curl -s -X POST http://localhost:8080/api/v1/subscriptions/unsubscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01012345678","channelId":5,"targetStatus":"BASIC"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 403, code = SUB-002 ("허용되지 않는 해지 상태 전이입니다 : 구독 안함 -> 일반 구독")
```

---

## 시나리오 5 — 회원 미존재 / 휴대폰 형식 오류

```bash
# 미존재 회원 → 404
curl -s -X POST http://localhost:8080/api/v1/subscriptions/unsubscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"01099990000","channelId":5,"targetStatus":"NONE"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 404, code = MEM-001

# 휴대폰 형식 오류 → 400
curl -s -X POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"phoneNumber":"abc","channelId":1,"targetStatus":"BASIC"}' \
  -w '\nHTTP %{http_code}\n'
# → HTTP 400, code = MEM-002
```

---

## 시나리오 6 — 풀 사이클 (Saga + Attempt 동작 검증)

핵심 — csrng 가 random(0/1) 응답이라 같은 호출도 결과가 달라진다. **모든 시도는 row 로 보존되지만 사용자 이력엔 COMMITTED 만 노출** ([ADR-0002](adr/0002-saga-with-attempt-state-machine.md)).

```bash
PHONE="01088$(printf %06d $RANDOM)"
echo "phone=$PHONE"

# 1) BASIC 가입
curl -sX POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"phoneNumber\":\"$PHONE\",\"channelId\":1,\"targetStatus\":\"BASIC\"}"

# 2) PREMIUM 업그레이드
curl -sX POST http://localhost:8080/api/v1/subscriptions/subscribe \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"phoneNumber\":\"$PHONE\",\"channelId\":2,\"targetStatus\":\"PREMIUM\"}"

# 3) 이력 조회
curl -s "http://localhost:8080/api/v1/subscriptions/history?phoneNumber=$PHONE" | jq
```

**관찰 포인트**:
- `status: "COMMITTED"` → `currentStatus` 가 변경됨
- `status: "ROLLED_BACK"` (csrng=0) → `currentStatus` 변화 없음, `failureReason: "EXTERNAL_REJECTED"`
- `status: "FAILED"` (외부 호출 자체 실패) → `failureReason: "EXTERNAL_TIMEOUT" | "EXTERNAL_PARSE_FAILURE" | ...`
- 이력 조회는 COMMITTED 만 보여줌 — 실패/롤백 시도는 사용자에게 안 보임

운영에선 SQL 로 모든 시도 추적:
```bash
docker exec alt-mysql mysql -ualt -palt alt -e \
  "SELECT id, status, failure_reason, requested_at FROM subscription_attempt ORDER BY id DESC LIMIT 10;"
```

---

## 응답 포맷

### 성공
```json
{ "data": { ... } }
```

### 실패 (RFC 7807 problem+json)
```json
{
  "type": "about:blank",
  "title": "회원을 찾을 수 없습니다",
  "status": 404,
  "detail": "회원을 찾을 수 없습니다 : phoneNumber=01099990000",
  "instance": "/api/v1/subscriptions/unsubscribe",
  "code": "MEM-001",
  "timestamp": "2026-05-11T03:05:18.968Z",
  "traceId": "fa67eba6-cbdf-41fa-aaa0-dc32b102e021"
}
```

도메인 `ErrorCode` → HTTP status 매핑은 `adapter-in/common/error/SubscriptionErrorMapper`.

---

## 에러 코드 일람

| 코드 | HTTP | 영역 | 설명 |
|---|---|---|---|
| `MEM-001` | 404 | Member | 회원을 찾을 수 없습니다 |
| `MEM-002` | 400 | Member | 유효하지 않은 휴대폰 번호 |
| `CHN-001` | 404 | Channel | 채널을 찾을 수 없습니다 |
| `CHN-002` | 403 | Channel | 해당 채널에서는 구독할 수 없습니다 |
| `CHN-003` | 403 | Channel | 해당 채널에서는 해지할 수 없습니다 |
| `SUB-001` | 403 | Subscription | 허용되지 않는 구독 상태 전이 |
| `SUB-002` | 403 | Subscription | 허용되지 않는 해지 상태 전이 |
| `SUB-201` | 409 | Subscription | 동일 멱등성 키로 이미 처리된 요청 |
| `MISSING_HEADER` | 400 | Common | 필수 헤더 누락 (예: `Idempotency-Key`) |
| `VALIDATION_FAILED` | 400 | Common | 요청 DTO 검증 실패 |

`failure_reason` (Attempt 종착 시 채워지는 도메인 enum, 외부 implementation 모름):
| 값 | 의미 |
|---|---|
| `EXTERNAL_REJECTED` | ROLLED_BACK 사유 — 외부가 명시적 거절 (csrng=0) |
| `EXTERNAL_TIMEOUT` | FAILED — 외부 호출 타임아웃 |
| `EXTERNAL_SERVER_ERROR` | FAILED — 외부 5xx |
| `EXTERNAL_CLIENT_ERROR` | FAILED — 외부 4xx |
| `EXTERNAL_CIRCUIT_OPEN` | FAILED — CircuitBreaker OPEN |
| `EXTERNAL_PARSE_FAILURE` | FAILED — 응답 파싱 실패 |
| `EXTERNAL_UNKNOWN` | FAILED — 분류되지 않은 실패 |

---

## 회복탄력성 메트릭 확인

Resilience4j CircuitBreaker / Retry 동작을 메트릭으로 직접 확인:

```bash
curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls | jq
```

---

## 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| `app` 컨테이너가 재시작 루프 | MySQL 헬스체크 통과 전 기동 가능성 — `docker compose down -v && docker compose up --build` |
| `Flyway migration failed` | 시드 충돌 — `docker compose down -v` 로 볼륨 초기화 |
| `summary` 가 항상 `null` | 키 미설정 / quota 부족 / OpenAI 장애 — `docker logs alt-app | grep -i llm` 으로 확인 |
| 포트 충돌 (3306 / 8080) | 호스트 기존 MySQL/앱 종료 후 재시도 |
| `docker compose` 미인식 | 구버전: `docker-compose up --build` |
