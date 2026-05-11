# ADR-0002. csrng 트랜잭션을 Saga + Attempt 상태 머신으로 처리

- Status: Accepted
- Date: 2026-05-08

## Context

요구사항(과제 명세):

> 구독하기 API와 구독 해지 API는 외부 API(csrng)를 호출하고, 응답 결과에 따라 트랜잭션을 처리합니다.
> - random=1 → 정상 처리, 트랜잭션 커밋
> - random=0 → 예외 발생, 트랜잭션 롤백

또한 **"외부 API 호출 시 발생할 수 있는 장애 상황에 대한 대응 전략"** 이 명시 평가 항목.

핵심 제약:
- csrng는 응답이 random + 외부 API라 응답 시간이 불확실. timeout / 5xx / network failure가 일상적으로 발생할 수 있음.
- 트랜잭션 처리는 정합성 보장 필수.
- 운영 관점에서 외부 API 장애 시 가시성(어떤 시도가 어떤 사유로 실패했나) 필요.

## 검토한 대안

### Option A — 트랜잭션 안에서 csrng 호출 + RuntimeException으로 강제 롤백

```
@Transactional
void subscribe(...) {
   member.applySubscribe(target);
   memberRepo.save(member);
   if (csrng.draw() == 0) throw new RuntimeException(); // 강제 롤백
}
```

- 장점: 코드가 가장 단순. 트랜잭션 의미가 명확.
- 단점:
  - **DB 커넥션 풀 고갈 리스크**. csrng 응답 시간(timeout 2s 가정) 동안 커넥션 점유. 동시 요청이 많아지면 풀이 마름.
  - csrng 5xx / 네트워크 장애 케이스가 트랜잭션 롤백과 구분되지 않아 운영 디버깅 어려움.
  - 시도 자체가 기록되지 않음 → 외부 API 실패율 SLA 측정 불가.
  - 평가 항목 "외부 API 장애 대응" 답변이 약함.

### Option B — 트랜잭션 밖에서 호출, 성공 시에만 트랜잭션 열어 커밋

```
int result = csrng.draw(); // TX 밖
if (result == 1) {
   transactionTemplate.execute(() -> { member.applySubscribe(target); ... });
}
```

- 장점: DB 커넥션 점유 시간 최소화.
- 단점:
  - 시도 이력이 남지 않음. 운영 가시성 부족.
  - 외부 호출 실패와 random=0 결과가 구분되지 않음 (둘 다 "변경 안 함"으로만 보임).
  - 평가 항목 답변이 약함 (장애 대응 전략의 가시성 부분).

### Option C — Saga + Attempt 상태 머신 (채택)

- Attempt를 별도 Aggregate로 도입해 모든 시도를 row로 영구 기록.
- csrng 호출은 트랜잭션 밖, 결과 분기 후 짧은 트랜잭션으로 종착 상태 갱신.

## Decision

**Option C 채택.**

### 흐름

```
[1] 짧은 TX: SubscriptionAttempt(status=PENDING) INSERT
                                                        ┐
[2] TX 밖: csrng 호출 (Resilience4j 적용)              │ 외부 응답 대기 동안 DB 커넥션 미보유
                                                        │
[3] 짧은 TX: 결과 분기                                  ┘
    random=1 → Member.applySubscribe(target) + Attempt.commit()    → COMMITTED
    random=0 → Attempt.rollback()                                  → ROLLED_BACK
    호출 실패 → Attempt.fail(CSRNG_UNAVAILABLE)                     → FAILED
```

### SubscriptionAttempt 상태 머신

```
PENDING ──commit────→ COMMITTED   (terminal)
        ──rollback──→ ROLLED_BACK (terminal, reason=CSRNG_REJECTED)
        ──fail──────→ FAILED      (terminal, reason=CSRNG_UNAVAILABLE)
```

- terminal 상태에서는 더 이상 변경 불가 (도메인 객체 `ensurePending()`로 강제).
- Attempt는 한 시도당 한 row만 존재.

### 외부 API 회복탄력성 정책 (Resilience4j)

| 패턴 | 목적 | 설정 |
|---|---|---|
| TimeLimiter | 호출당 timeout | csrng 2s |
| Retry | 5xx/네트워크 한정 지수 백오프 | 100ms × 2회 |
| CircuitBreaker | 실패율 임계 초과 시 OPEN | 50% / 10 호출 |
| Bulkhead | 외부 호출 동시성 제한 | 적용 |

## 선택 근거

- **DB 커넥션 점유 최소화** — 외부 API 응답 대기 중 커넥션을 잡지 않는다. 동시 요청이 폭증해도 커넥션 풀이 외부 API 응답 시간에 영향받지 않음.
- **운영 가시성** — 모든 시도가 row로 남아 외부 API 실패율, SLA, 사용자별 시도 패턴을 SQL로 즉시 추적 가능.
- **장애 분기 명확성** — `ROLLED_BACK`(csrng가 0 응답) vs `FAILED`(csrng 호출 자체 실패)가 도메인 모델에서 구분된다. 운영 알람과 사용자 대응 정책을 다르게 갈 수 있음.
- **평가 항목 직접 충족** — "외부 API 장애 대응 전략"에 대한 답이 흐름과 모델로 구체화되어 있음.
- **멱등성과의 결합** — Attempt.idempotency_key UNIQUE가 멱등성 안전망이 됨 ([ADR-0004](0004-idempotency-strategy.md)).

## 장점

- (+) 모든 시도가 이력에 남아 외부 API 장애 시 가시성 확보.
- (+) DB 커넥션 점유 시간 최소화 → 외부 API 응답 시간이 전체 처리량에 직접 영향을 안 줌.
- (+) 구독 이력 조회는 COMMITTED만 노출, 운영 어드민에서는 ROLLED_BACK / FAILED도 추적 가능.
- (+) Resilience4j 설정이 어댑터에 캡슐화되어 도메인이 무지(無知).
- (+) Attempt가 이벤트 로그 역할 → Outbox 패턴 없이도 후속 사이드이펙트(알림, 결제) 추가 시 폴링/스트리밍으로 확장 가능.

## 단점 / 비용

- (-) 트랜잭션이 두 번 분리됨 → INSERT 1회 + UPDATE 1회. 단순 시나리오 대비 DB 라운드트립이 1회 추가.
- (-) "PENDING으로 죽은 Attempt"가 이론적으로 가능 (애플리케이션이 csrng 응답 받고 UPDATE 하기 전에 다운). 운영상 일정 시간 지난 PENDING은 스케줄러로 FAILED 처리 필요 (현재 미구현, 향후 보강 항목).
- (-) Attempt 테이블이 커진다 (모든 시도 영구 보존). 파티셔닝 / 아카이빙 정책이 운영 단계에서 필요.

## 평가

Option A의 단순성은 매력적이지만 외부 API 장애가 일상적인 컨텍스트에서 커넥션 풀 고갈은 받아들일 수 없는 리스크. Option B는 가시성 부족으로 평가 항목 답변이 약하다. Option C는 비용(트랜잭션 분리, 테이블 증가)이 명확하지만 그 비용으로 사는 가치(가시성 + 격리 + 멱등성 결합)가 크다.
