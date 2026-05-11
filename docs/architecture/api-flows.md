# API 데이터 흐름

3개 엔드포인트의 흐름을 단계별로 정리. 각 흐름은 어떤 객체가 어떤 트랜잭션 경계를 갖는지에 초점.

---

## 1. 구독 신청 — `POST /api/v1/subscriptions/subscribe`

### 요청
```http
POST /api/v1/subscriptions/subscribe
Idempotency-Key: 9c3f...uuid
Content-Type: application/json

{
  "phoneNumber": "01012345678",
  "channelId": 1,
  "toStatus": "PREMIUM"
}
```

### 흐름

```
[adapter-in] SubscriptionCommandController
   │  (요청 → SubscribeCommand 변환, Idempotency-Key 헤더 포함)
   ▼
[application] SubscribeService.execute(command)
   │  IdempotencyRegistryPort.executeOnce(key, supplier)
   │  └─ Caffeine 캐시 1차 차단: 같은 key 재요청 → IdempotencyConflictException(409)
   │
   ├─ [TX-1, Coordinator] MemberRegistrationCoordinator.findOrRegister
   │     ├─ MemberQueryPort.findByPhoneNumber → 있으면 그대로
   │     └─ 없으면 MemberCommandPort.save(newMember)
   │
   ├─ Bundle.isRegistrationOnly() == true ?
   │     └─ "신규 가입 + 구독 안 함" 케이스는 여기서 종료, csrng 호출 X
   │
   ├─ [읽기, Manager] ChannelReadManager.getById(channelId)
   │     └─ 채널 존재 + canSubscribe() 검증 (도메인 위임)
   │
   ├─ Bundle.verifyTransition()
   │     └─ SubscriptionTransitionPolicy.assertSubscribable(from, to) — 도메인 정책 검증
   │
   └─ [Coordinator] SubscribeCoordinator.coordinate(bundle)
         │
         ├─ [TX-2 짧은 트랜잭션]
         │     SubscriptionAttemptCommandPort.save(Attempt(PENDING))
         │     │
         │     └─ DB UNIQUE(idempotency_key) 2차 차단:
         │        Caffeine TTL 만료 후 같은 key 재시도 → 여기서 차단
         │
         ├─ [TX 밖] RandomClient.draw()  ← csrng 호출
         │     └─ Resilience4j: TimeLimiter(2s) + Retry(2회) + CB + Bulkhead
         │     └─ 응답: random=0 / 1 / 호출 자체 실패
         │
         └─ [TX-3 짧은 트랜잭션] 결과 분기 — SubscriptionPersistenceFacade
               ├─ random=1 → Member.applySubscribe(target) + Attempt.commit() → COMMITTED
               ├─ random=0 → Attempt.rollback()                              → ROLLED_BACK
               └─ 호출 실패 → Attempt.fail(CSRNG_UNAVAILABLE)                → FAILED
```

### 응답 매핑

| 결과 | HTTP | data |
|---|---|---|
| COMMITTED | 200 | `{ "memberId", "status", "attemptId" }` |
| ROLLED_BACK | 200 | `{ "attemptId", "status": "ROLLED_BACK", "reason": "CSRNG_REJECTED" }` |
| FAILED | 200 | `{ "attemptId", "status": "FAILED", "reason": "CSRNG_UNAVAILABLE" }` |
| 도메인 검증 실패 | 422 | `{ "code": "INVALID_TRANSITION", ... }` |
| Idempotency 충돌 | 409 | `{ "code": "IDEMPOTENCY_CONFLICT", ... }` |

> **설계 포인트**: csrng 호출이 트랜잭션 밖에 있어 외부 응답 대기 동안 DB 커넥션을 점유하지 않는다. Attempt row가 모든 시도를 보존해 운영 가시성/SLA 측정 가능.

---

## 2. 구독 해지 — `POST /api/v1/subscriptions/unsubscribe`

흐름은 구독 신청과 거의 동일하다. 차이만 정리:

| 단계 | 차이 |
|---|---|
| Member 처리 | 신규 가입 없음 — 회원 미존재 시 `MEMBER_NOT_FOUND`(404) |
| Channel 검증 | `canUnsubscribe()` 검증 |
| 도메인 정책 | `SubscriptionTransitionPolicy.assertUnsubscribable(from, to)` |
| Attempt.kind | `UNSUBSCRIBE` |
| 상태 전이 | `PREMIUM → {BASIC, NONE}`, `BASIC → NONE` 만 허용 |

나머지 (Saga 흐름, csrng 호출, 결과 분기, 멱등성 처리) 는 동일.

---

## 3. 구독 이력 조회 — `GET /api/v1/subscriptions/history`

### 요청
```http
GET /api/v1/subscriptions/history?phoneNumber=01012345678
```

### 흐름

```
[adapter-in] SubscriptionQueryController
   │  (Query 객체 변환)
   ▼
[application] QuerySubscriptionHistoryService.execute(query)
   │
   ├─ [Manager] MemberReadManager.getByPhoneNumber
   │     └─ 미존재 시 MEMBER_NOT_FOUND(404)
   │
   ├─ [Facade] SubscriptionHistoryReadFacade
   │     │
   │     ├─ SubscriptionAttemptQueryPort.findCommittedByMemberId(memberId)
   │     │     └─ COMMITTED 만 노출 (ROLLED_BACK / FAILED 는 운영용)
   │     │     └─ requested_at DESC 정렬 (idx_attempt_member_requested 인덱스)
   │     │
   │     ├─ ChannelQueryPort.findByIds(...)
   │     │     └─ N+1 방지를 위해 한 번에 조회 후 메모리 매핑
   │     │
   │     └─ SubscriptionHistoryAssembler.assemble(member, attempts, channels)
   │           └─ ApplicationDTO(SubscriptionHistoryReadBundle) 조립
   │
   └─ [Facade] HistorySummaryRefreshFacade
         │
         ├─ HistorySummaryQueryPort.findByMemberId
         │     ├─ 캐시 hit + 최신 이력 기반 → 그대로 반환
         │     └─ 캐시 miss / stale → LLM 호출
         │
         ├─ [LLM 호출] LlmSummaryClient.summarize(history)
         │     ├─ 성공 → HistorySummaryCommandPort.save(summary) + 응답에 포함
         │     └─ 실패 → graceful degradation: summary = null, 이력은 정상 반환
         │
         └─ Result 조립
```

### 응답

```json
{
  "data": {
    "phoneNumber": "01012345678",
    "currentStatus": "PREMIUM",
    "history": [
      { "channel": "모바일앱", "kind": "SUBSCRIBE", "toStatus": "PREMIUM", "at": "2026-02-01T10:00:00" },
      { "channel": "홈페이지", "kind": "SUBSCRIBE", "toStatus": "BASIC",  "at": "2026-01-01T09:00:00" }
    ],
    "summary": "2026년 1월 1일 홈페이지를 통해 일반 구독으로 가입한 뒤, 2월 1일 모바일앱에서 프리미엄 구독하였습니다."
  },
  "error": null
}
```

LLM 실패 시:
```json
{
  "data": { "history": [...], "summary": null },
  "error": null
}
```

> **설계 포인트**: 핵심 기능(이력)을 LLM 가용성에 종속시키지 않는다. 자격증명이 없으면 `NoOpLlmClient`가 동작해 부팅 자체는 항상 가능.

---

## 트랜잭션 경계 한눈에

| 구간 | 트랜잭션 | DB 커넥션 점유 |
|---|---|---|
| Member 조회/생성 | TX-1 (Coordinator) | 짧음 |
| Attempt(PENDING) INSERT | TX-2 (Coordinator) | 짧음 |
| **csrng 호출** | **트랜잭션 밖** | **없음** |
| 결과 분기 (Attempt 종착 + Member 갱신) | TX-3 (Facade) | 짧음 |
| 이력 조회 (read-only) | 단일 TX | 짧음 |
| LLM 호출 | 트랜잭션 밖 | 없음 |

외부 호출이 트랜잭션 밖에 있다는 것이 본 시스템의 가장 중요한 설계 결정. 자세한 근거는 [ADR-0002](../adr/0002-saga-with-attempt-state-machine.md).
