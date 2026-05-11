# ADR-0004. 멱등성 전략 (Idempotency-Key 필수 + Caffeine 게이트 + DB UNIQUE)

## Context

ARTINUS 과제 명세는 멱등성을 명시적으로 요구하지 않는다. 그러나 다음 요인들이 멱등성을 사실상 필수로 만든다:

1. **Resilience4j Retry** — 평가 항목인 "외부 API 장애 대응"을 충족하려면 Retry가 필요하고, Retry는 본질적으로 같은 요청을 여러 번 보낸다.
2. **클라이언트 재시도** — 웹/모바일/콜센터 시스템도 네트워크 일시 장애 시 재시도하는 게 일반적.
3. **응답 손실** — csrng 응답이 늦게 도착해 클라이언트가 timeout으로 판단했지만 서버는 이미 트랜잭션을 커밋한 케이스가 가능.

멱등성 없이 재시도하면:
- 동일 회원이 짧은 시간에 여러 번 PREMIUM으로 전이 (같은 toStatus라 도메인 검증은 통과하지만 이력에 중복 기록).
- csrng가 random 응답이라 본질적으로 멱등 불가능 → 재시도마다 다른 결과를 받을 수 있음.
- 외부 결제·통신 시스템과 연계될 경우 비용/사용자 알림 중복 (현재 과제엔 없지만 일반 구독 서비스 컨벤션).

## 검토한 대안

### Option L0 — 멱등성 없음

- Retry / 클라이언트 재시도가 그대로 중복을 만든다.
- 채택 불가.

### Option L1 — DB UNIQUE만

- `subscription_attempt.idempotency_key` UNIQUE 제약만 둠.
- 장점: 단순.
- 단점: **외부 호출은 매번 일어남**. csrng 호출 자체를 차단하지 못해 의미가 약함. INSERT 시점에서야 차단됨.

### Option L1.5 — Caffeine 단인스턴스 게이트 + DB UNIQUE (채택)

- Caffeine 캐시(TTL 5분)가 1차 게이트 — 같은 key 재요청은 외부 호출 자체 차단.
- DB UNIQUE가 2차 안전망 — 캐시 만료 후나 분산 환경 노드 캐시 미스 시.

### Option L2 — Redis SETNX + DB UNIQUE 분산 안전망

- Redis SETNX로 분산 환경에서도 1차 차단.
- 장점: 분산 환경에서 외부 호출 중복까지 차단.
- 단점: Redis 인프라 추가. 본 과제 단일 인스턴스 가정에서는 과함.
- 진화 경로: 트래픽 커지면 cache 어댑터를 caffeine → redis로 모듈 단위 교체 가능.

### Option L3 — Outbox + 멱등 컨슈머

- 이벤트 발행 시스템에서 사용.
- 본 과제는 이벤트 발행 없음. SubscriptionAttempt가 이벤트 로그 역할 겸함.
- 채택 불가 (가치 없음).

## Decision

**Option L1.5 채택.** HTTP `Idempotency-Key` 헤더 필수 + Caffeine 단인스턴스 게이트 + DB UNIQUE 2중 안전망.

```
[1] 클라이언트가 Idempotency-Key 헤더로 UUID 전달 (필수, @RequestHeader required=true)
    → 누락 시 GlobalExceptionHandler가 HTTP 400 반환
[2] adapter-in이 헤더 → UseCase 입력으로 전달
[3] application Service가 IdempotencyRegistryPort.executeOnce(key, supplier)로 본 흐름 감쌈
    → Caffeine 캐시(TTL 5분 ± 1분 jitter)가 같은 key 동시/연속 호출의 외부 호출까지 차단
    → 같은 키 재요청 → IdempotencyConflictException → HTTP 409
[4] Service supplier 안에서 SubscriptionAttempt persist
    → DB UNIQUE 제약(subscription_attempt.idempotency_key)이 최후 안전망
    → 캐시 TTL 만료 후 재요청도 차단
```

### 정책

- **재요청 = 거절**: 같은 idempotency-key로 두 번째 요청이 오면 **HTTP 409 거절**. 첫 요청 결과를 그대로 반환하지 않는다.
  - 표준 Idempotency-Key 컨트랙트(Stripe / OpenAI 등)는 "같은 결과 반환"이지만 본 과제는 "중복 검출 + 거절"로 단순화.
  - 클라이언트는 재시도 시 새 UUID를 발급해야 함.

- **Caffeine 게이트의 역할**:
  - **외부 호출 차단** — 같은 키 재요청은 csrng 호출 자체를 안 일으킴 (lambda 미실행).
  - **Singleflight 보장** — 동시 같은 키 호출 시 한 스레드만 처리, 나머지는 대기 후 conflict.
  - supplier가 예외 throw 시 캐시 put 안 됨 → 재시도 가능 (도메인 검증 실패 등).

- **DB UNIQUE의 역할**:
  - 캐시 TTL 만료 후 같은 키 재요청 차단.
  - 분산 환경에서 노드 간 캐시 미스 시 중복 INSERT 차단.

### 변경 영역 요약

| 영역 | 변경 |
|---|---|
| domain | `SubscriptionAttempt.idempotencyKey` 필드, `SubscriptionErrorCode.IDEMPOTENCY_CONFLICT`, `IdempotencyConflictException` |
| application | `IdempotencyRegistryPort` (port/out, `<T> T executeOnce(String key, Supplier<T> action)`), `SubscribeService` / `UnsubscribeService`가 본문을 `executeOnce`로 감쌈 |
| adapter-out | `cache-caffeine` 모듈 신규, `CaffeineIdempotencyRegistry` (Caffeine `cache.get(key, mappingFn)` Singleflight) |
| persistence | `subscription_attempt.idempotency_key VARCHAR(64) NULL UNIQUE` |

## 선택 근거

- **외부 호출 중복 차단** — Caffeine 게이트가 lambda 자체를 안 돌림. csrng가 random이라 멱등 불가능한 점을 보완.
- **3중 강제** — HTTP 헤더 검증(400) + 캐시(409) + DB UNIQUE(unique constraint violation). 한 단계가 무너져도 다음 단계가 잡음.
- **단일 인스턴스에 충분** — 본 과제 트래픽 가정에서 Caffeine + DB로 외부 호출 / DB INSERT 모두 차단.
- **진화 친화** — 분산 환경 진화 시 `cache-caffeine` 어댑터를 `cache-redis`로 모듈 단위 교체. Application 코드 변경 없음.
- **클라이언트 컨트랙트 명시** — Idempotency-Key 누락 = 400으로 즉시 차단. 클라이언트 책임 경계가 명확.

## 장점

- (+) 외부 호출 중복까지 차단 (Caffeine 게이트가 lambda를 안 돌림).
- (+) 중복 처리 방지가 도메인/스키마/캐시 3중으로 강제됨.
- (+) Idempotency-Key 누락 = 400으로 명확히 거절 — 클라이언트 컨트랙트 명시.
- (+) Resilience4j Retry와 자연스럽게 결합 — 같은 키로 재호출되어도 안전.
- (+) 어댑터 교체로 분산 환경 진화 가능 — Application 코드 변경 없음.

## 단점 / 비용

- (-) 동일 키 + 다른 페이로드 케이스(키 충돌)에 대한 정책 필요. 본 과제에서는 첫 시도 결과로 차단(HTTP 409). 페이로드 검증까지 가는 것은 오버.
- (-) 단일 인스턴스 한정 게이트 — 분산 환경에서는 노드 간 캐시 미스 시 외부 호출 중복 가능. DB UNIQUE로 INSERT는 차단되지만 외부 호출 1회는 일어남.
- (-) Idempotency-Key 필수화 — 클라이언트가 매 요청마다 UUID 발급 책임을 가짐.

## 채택하지 않은 옵션 정리

| Option | 이유 |
|---|---|
| L0 (없음) | Retry / 재시도가 그대로 중복 발생 |
| L1 (DB UNIQUE만) | 외부 호출 차단 못 함 |
| L2 (Redis SETNX) | 단일 인스턴스 가정에서 인프라 과함. 진화 시 어댑터 교체로 대응 |
| L3 (Outbox) | 이벤트 발행 없음. Attempt가 이미 이벤트 로그 역할 |
| 표준 시맨틱 (첫 응답 재현) | 응답 페이로드 직렬화/캐싱 비용. 클라이언트가 새 UUID 쓰는 게 표준 패턴 |

## 평가

Option L2가 분산 환경에서 더 안전하지만 본 과제 규모/트래픽 가정에서는 인프라 추가 비용이 가치를 넘지 못한다. L1.5는 단일 인스턴스에서 외부 호출 중복까지 차단하면서, 분산 환경 진화 시 어댑터 교체만으로 L2로 갈 수 있는 진화 경로를 열어둔다.
