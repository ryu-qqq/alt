# ADR-0004. 멱등성 전략 (Idempotency-Key + DB UNIQUE)

- Status: Accepted
- Date: 2026-05-09

## Context

ARTINUS 과제 명세는 멱등성을 명시적으로 요구하지 않는다. 그러나 평가 항목인 "외부 API 장애 대응"을 충족하려면 Resilience4j Retry 가 필요하고, Retry 는 **본질적으로 같은 요청을 두 번 보낼 수 있다**.

추가 위험 요인:
- 클라이언트(웹/모바일/콜센터 시스템)도 네트워크 일시 장애 시 재시도하는 게 일반적이다.
- csrng 응답이 늦게 도착해 클라이언트는 timeout 으로 판단했지만 서버는 이미 트랜잭션을 커밋한 케이스가 가능하다.

멱등성 없이 재시도하면:
- 동일 회원이 짧은 시간에 여러 번 PREMIUM 으로 전이됨 → 같은 toStatus 라 도메인 검증은 통과하지만 이력에 중복 기록.
- 외부 결제·통신 시스템과 연계될 경우 비용/사용자 알림 중복 발생 (현재 과제엔 없지만 일반 구독 서비스 컨벤션).

## Decision

**L1: HTTP `Idempotency-Key` 헤더 + DB UNIQUE 인덱스** 패턴으로 시작.

```
[1] 클라이언트가 Idempotency-Key 헤더로 UUID 전달 (선택)
[2] adapter-in 이 헤더 → UseCase 입력으로 전달
[3] application 이 SubscriptionAttempt.forNew(..., idempotencyKey) 생성 → repository.save
[4] DB UNIQUE 충돌 발생 시 IdempotencyConflictException → 기존 시도 결과 조회 후 동일 응답 반환
```

도메인 변경:
- `SubscriptionAttempt.idempotencyKey` (nullable String)
- `SubscriptionErrorCode.IDEMPOTENCY_CONFLICT`
- `IdempotencyConflictException`

영속 변경 (예정):
- `subscription_attempt.idempotency_key VARCHAR(64) NULL UNIQUE`

## Consequences

- (+) 중복 처리 방지가 도메인/스키마 레벨에서 강제된다.
- (+) Idempotency-Key 누락 요청도 허용 (nullable) — 점진적 도입 가능.
- (+) Resilience4j Retry 와 자연스럽게 결합 — 같은 키로 재호출되어도 안전.
- (-) 동일 키 + 다른 페이로드 케이스(키 충돌)에 대한 정책 필요. 본 과제 범위에서는 첫 시도 결과를 그대로 반환 (HTTP 200) 하도록 함. 페이로드 검증까지 가는 것은 오버.
- (-) UNIQUE 충돌은 DB 레벨 race 가 있을 때 InsertUnique 시점에서만 잡힌다. 어댑터에서 SQL 예외를 도메인 예외로 매핑.

## Why not L2 (Redis SETNX 2중 안전망)

voice 프로젝트에서는 Redis SETNX 로 1차 차단, DB UNIQUE 로 2차 차단하는 2중 안전망을 썼다. ARTINUS 과제는 트래픽 규모가 명시되지 않았고, csrng 호출 자체가 동시성 핵심 병목이 아니므로 L1 만으로 충분하다고 판단. 트래픽이 커지면 어댑터를 확장하는 것으로 진화 가능 (ConfigurationOption).

## Why not Outbox + 멱등 컨슈머

이벤트 발행이 없는 단일 서비스 구조이므로 불필요. SubscriptionAttempt 자체가 이벤트 로그 역할을 겸하므로 Outbox 의 "발행 보장" 가치가 살지 않는다.
