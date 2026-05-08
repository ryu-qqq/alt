# ADR-0002. csrng 트랜잭션을 Saga + Attempt 상태 머신으로 처리

- Status: Accepted
- Date: 2026-05-08

## Context

요구사항(과제 명세):

> 구독하기 API와 구독 해지 API는 외부 API(csrng)를 호출하고, 응답 결과에 따라 트랜잭션을 처리합니다.
> - random=1 → 정상 처리, 트랜잭션 커밋
> - random=0 → 예외 발생, 트랜잭션 롤백

또한 "외부 API 호출 시 발생할 수 있는 장애 상황에 대한 대응 전략"이 명시 평가 항목.

순진한 두 가지 접근의 문제:

| 접근 | 문제 |
|---|---|
| (A) 트랜잭션 안에서 csrng 호출 → random=0이면 RuntimeException 던져 강제 롤백 | 외부 API 응답 시간 동안 DB 커넥션 점유. csrng는 빈번히 다운되는 외부 API라 커넥션 풀 고갈 리스크. |
| (B) 트랜잭션 밖에서 호출 → 성공 시에만 DB 트랜잭션 열어 커밋 | 시도 자체가 추적되지 않음. 운영 관점 가시성/디버깅 어려움. 장애 대응 평가 항목 약화. |

## Decision

**Saga + Attempt 상태 머신** 도입.

### 흐름

```
[1] SubscriptionAttempt(status=PENDING) INSERT  ─┐
                                                  │ 짧은 트랜잭션 (멤버 상태 변경 X)
[2] csrng 호출 (트랜잭션 밖, Resilience4j 적용)  │
                                                  │
[3] 결과 분기 — 짧은 트랜잭션:                   │
    random=1 → Member.applySubscribe(target)      │
              + Attempt.commit()                  │
    random=0 → Attempt.rollback()                 │
    호출 실패 → Attempt.fail(CSRNG_UNAVAILABLE) ─┘
```

### SubscriptionAttempt 상태 머신

```
PENDING ──commit────→ COMMITTED   (terminal)
        ──rollback──→ ROLLED_BACK (terminal, reason=CSRNG_REJECTED)
        ──fail──────→ FAILED      (terminal, reason=CSRNG_UNAVAILABLE)
```

- terminal 상태에서는 더 이상 변경 불가 (도메인 객체 `ensurePending()`로 강제).
- Attempt는 한 시도당 한 row만 존재. 결과가 늦게 오더라도 멱등하게 갱신.

### 외부 API 회복탄력성 정책 (Resilience4j)

- TimeLimiter: 호출 자체에 짧은 timeout (예: 2s)
- Retry: 5xx/네트워크 오류 한정, 지수 백오프 (예: 100ms × 2)
- CircuitBreaker: 일정 실패율 초과 시 OPEN → 빠른 실패 (FAILED 처리)
- Bulkhead: 외부 API 호출 동시성 제한

## Consequences

- (+) **모든 시도가 이력에 남아** 외부 API 장애 시 가시성 확보 (운영 디버깅, SLA 측정).
- (+) **DB 커넥션 점유 시간 최소화**. 외부 API 응답 대기 동안 커넥션 미보유.
- (+) 구독 이력 조회 API는 COMMITTED만 노출. 운영 어드민에서 ROLLED_BACK / FAILED도 추적 가능.
- (+) Resilience4j 설정이 어댑터 안에 캡슐화되어 도메인이 무지(無知)함.
- (-) 트랜잭션이 두 번 분리됨 → INSERT 1회 + UPDATE 1회. 트래픽 가정 하에 부담은 무시할 수준.
- (-) "PENDING으로 죽은 Attempt" 이론적으로 가능 (애플리케이션이 csrng 응답 받고 UPDATE 하기 전에 다운). 운영상 PENDING이 일정 시간 지속되면 스케줄러로 FAILED 처리 (별도 작업).
