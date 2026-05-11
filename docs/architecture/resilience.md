# 외부 API 장애 대응 전략

본 시스템은 두 외부 API에 의존한다 — `csrng` (구독/해지 결과 결정), `OpenAI` (이력 자연어 요약, gpt-4o-mini). 두 API 모두 가용성을 통제할 수 없으므로 다층 방어가 필요하다.

## 1. 트랜잭션 외부에서 호출 (Saga + Attempt)

csrng는 외부 API라 응답 시간이 불확실하다. 트랜잭션 안에서 호출하면 DB 커넥션을 점유한 채 외부를 기다리게 되어 **커넥션 풀 고갈 → 전체 서비스 마비** 리스크가 크다.

대신 다음 흐름을 채택:

```
[1] 짧은 TX: SubscriptionAttempt(PENDING) INSERT
[2] TX 밖: csrng 호출 (Resilience4j 적용)
[3] 짧은 TX: 결과 분기
    random=1 → Member.applySubscribe(target) + Attempt.commit()
    random=0 → Attempt.rollback()
    호출 실패 → Attempt.fail(CSRNG_UNAVAILABLE)
```

→ 모든 시도가 row로 보존되어 **운영 가시성 + SLA 측정 가능**.

자세한 근거: [ADR-0002](../adr/0002-saga-with-attempt-state-machine.md)

## 2. Resilience4j 정책 (어댑터에 캡슐화)

`adapter-out/client-csrng`, `adapter-out/client-llm`의 메서드에 직접 어노테이션 적용. 도메인은 무지(無知).

| 패턴 | 목적 | csrng | llm |
|---|---|---|---|
| **TimeLimiter** | 호출당 timeout | 2s | 5s |
| **Retry** | 5xx / 네트워크 오류 한정 지수 백오프 | 100ms × 2 | 500ms × 2 |
| **CircuitBreaker** | 실패율 임계 초과 시 OPEN — 빠른 실패 | 50% / 10 호출 | 50% / 10 호출 |
| **Bulkhead** | 외부 API 동시 호출 제한 — 다른 흐름 보호 | 적용 | 적용 |

설정 위치: `adapter-out/client-csrng/src/main/resources/csrng-client.yml`, `adapter-out/client-llm/src/main/resources/llm-client.yml`. 각 어댑터가 자기 설정을 들고 있고, 진입점(`bootstrap-api`의 `application.yml`)이 `spring.config.import`로 합쳐 온다.

## 3. LLM 실패 = Graceful Degradation

이력 조회 API는 LLM 요약 실패 시 `summary: null`로 응답. 이력 자체는 항상 반환 → 핵심 기능을 LLM 가용성에 종속시키지 않는다.

자격증명 없을 때를 위해 `NoOpLlmClient`가 존재 — 환경변수 `OPENAI_API_KEY` 미설정 시 자동 활성화되어 부팅 자체는 항상 가능.

## 4. 멱등성 — Retry와 안전하게 결합

Resilience4j Retry는 본질적으로 같은 요청을 여러 번 보낸다. 멱등성이 없으면 외부 호출/DB 변경이 중복 발생한다.

```
[1] 클라이언트가 Idempotency-Key 헤더로 UUID 전달 (필수, 누락=400)
[2] Caffeine 게이트 (TTL 5분 ± jitter) — 같은 key 재요청은 외부 호출 자체 차단
[3] DB UNIQUE(subscription_attempt.idempotency_key) — 캐시 만료 후 / 분산 환경 안전망
```

같은 키 재요청은 **HTTP 409로 거절**. 표준 Idempotency-Key 시맨틱(첫 응답 재현)은 채택하지 않음.

자세한 근거: [ADR-0004](../adr/0004-idempotency-strategy.md)

## 5. 알려진 제약

| 시나리오 | 현재 동작 | 운영 보강 방안 |
|---|---|---|
| 애플리케이션이 csrng 응답 받기 전 다운 | PENDING attempt 좀비로 남음 | 일정 시간 지난 PENDING을 FAILED로 정리하는 스케줄러 |
| 분산 환경에서 노드 간 캐시 미스 | DB UNIQUE는 차단하나 외부 호출 1회 가능 | Redis 어댑터로 교체 (ADR-0004 참고) |
| csrng 영구 장애 | CB가 OPEN으로 빠른 실패 → 모든 요청이 FAILED | 운영 알람 + 사용자 안내 채널 |
