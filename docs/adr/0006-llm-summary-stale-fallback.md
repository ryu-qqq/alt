# ADR-0006. LLM 요약 응답 정책 — stale fallback + 신선도 메타 노출

## Context

이력 조회 응답의 `summary` 필드는 LLM(OpenAI gpt-4o-mini) 호출 결과다. 외부 API라 일시적 실패가 일상적이며, [ADR-0002](0002-saga-with-attempt-state-machine.md) / [ADR-0004](0004-idempotency-strategy.md) 와 마찬가지로 어떻게 graceful 처리할지 정책 결정이 필요하다.

특히 **fingerprint 불일치 (이력 변경 후) + LLM 재호출 실패** 케이스의 응답을 어떻게 만들지가 핵심이다. 이 케이스에서 영속체 `history_summary` 에는 옛 fingerprint 의 요약이 남아있다.

## 검토한 대안

### Option A — null 박기 (LLM 실패 = summary 없음)

응답:
```json
{ "summary": null }
```

- 장점: 정확. 잘못된 정보 노출 X.
- 단점:
  - 사용자 UX 저하 — 요약이 안 보임.
  - 클라이언트가 "왜 안 보이는지" 알 방법 없음 (외부 일시 실패 vs 영구 실패 구분 불가).

### Option B — 영속체 stale 그대로 (조용히 옛 요약 반환)

응답:
```json
{ "summary": "2026년 1월 ... (옛 이력 기반)" }
```

- 장점: UX 좋음.
- 단점:
  - 사용자가 PREMIUM 으로 업그레이드한 직후 응답에 "BASIC 구독 중" 요약이 보임 → **혼란 / 클레임 유발**.
  - 클라이언트 입장에서 "이 요약이 최신인지 stale 인지" 알 방법 없음.

### Option C — stale + 신선도 메타 노출 (채택)

응답:
```json
{
  "summary": "2026년 1월 ... (옛 이력 기반)",
  "summaryGeneratedAt": "2026-04-01T10:00:00Z",
  "summaryStale": true
}
```

- 장점:
  - UX + 정확성 모두 확보 — 옛 요약을 보여주되 "최근 게 아님" 시그널을 함께 노출.
  - 클라이언트가 `summaryStale` 또는 `summaryGeneratedAt` 으로 판단 위임 가능 (예: "최근 요약이 아닐 수 있습니다" 안내).
  - Stripe / GitHub API 같은 시스템에서 이미 검증된 패턴.
- 단점:
  - 응답 구조가 약간 복잡 (단일 string → 3개 필드).
  - 클라이언트가 stale 케이스를 적극 처리하지 않으면 Option B 와 동일한 위험 (다만 책임은 명시적으로 클라이언트로 이관).

## Decision

**Option C 채택.**

### 응답 정책 우선순위

```
fresh (LLM 성공 또는 fingerprint 일치)
  > stale fallback (LLM 실패 + 영속체 있음)
  > null (LLM 실패 + 영속체 없음 / 이력 0건)
```

### 응답 필드

| 필드 | 의미 | null 케이스 |
|---|---|---|
| `summary` | 자연어 요약 본문 | 이력 0건 또는 LLM 실패 + 폴백 없음 |
| `summaryGeneratedAt` | 요약 생성 시각 (Instant ISO-8601) | summary == null 일 때 |
| `summaryStale` | true 면 LLM 재호출 실패로 영속체 옛 요약 폴백한 케이스 | (boolean — 항상 값 있음) |

### 구현 흐름 (`QuerySubscriptionHistoryService.resolveSummary`)

```
1. COMMITTED 0건                              → empty()                              # null/null/false
2. fingerprint 일치하는 영속체 있음            → success(persisted)                   # 영속체값/generatedAt/false
3. fingerprint 불일치 → LLM 호출
   3-1. 성공                                  → success(fresh)                       # LLM값/now/false
   3-2. 실패 + 영속체 있음                     → staleSuccess(persisted)              # 영속체값/generatedAt/true
   3-3. 실패 + 영속체 없음                     → unavailable                          # null/null/false
```

### DB 영속 정책

- LLM 호출 **성공 시에만** `history_summary` UPSERT (fingerprint 갱신).
- LLM 실패 시 영속체 **미갱신** — 다음 호출에 재시도 기회 보존.
- `generatedAt` 은 BaseAuditEntity 의 `updatedAt` 매핑 (별도 컬럼 불필요).

## 선택 근거

- **신뢰성 시그널 명시화** — null vs stale vs fresh 를 응답 메타로 구분. 클라이언트가 비즈니스 임팩트에 맞춰 처리 가능.
- **데이터 정확성 경고 동반** — 옛 요약을 보여주되 항상 "이건 stale" 신호 동반. 잘못된 정보 노출 위험 최소화.
- **운영 관측성 향상** — `summaryStale=true` 응답 비율을 모니터링하면 LLM 가용성 저하를 즉시 감지 가능.
- **재시도 기회 보존** — 영속체 미갱신 → 다음 호출에 자동 재시도 → eventual fresh.

## 장점

- (+) UX 와 정확성을 동시에 확보 — 옛 요약 노출 + stale 신호.
- (+) 운영 측면 모니터링 지표 자연 확보 (`stale=true` 비율, `generatedAt` 분포).
- (+) 다음 호출 자동 재시도로 LLM 일시 장애 자동 회복.
- (+) 클라이언트가 stale 케이스를 모르면 → Option B 와 동일 동작 (backward 친화).

## 단점 / 비용

- (-) 응답 구조 약간 복잡 — `summary` 단일 string 에서 3개 필드로 확장.
- (-) 클라이언트가 stale 케이스를 명시적으로 처리해야 진가가 살아남.
- (-) 영속체 옛 요약이 "최초 생성 시점부터 영원히 stale" 인 케이스 가능 (LLM 영구 장애).
  - 완화: `summaryGeneratedAt` 임계 초과 시 클라이언트 측에서 "오래된 요약입니다" 표시 가능.

## 평가

Option A 는 정확하지만 UX 비용이 크고, Option B 는 UX 가 좋지만 데이터 정확성 위험이 크다. Option C 는 응답 메타 추가라는 작은 비용으로 두 옵션의 단점을 모두 회피한다. 본 도메인에서 요약은 **보조 정보** (이력 자체가 핵심) 이라 stale 노출이 시스템 본질을 해치지 않으면서, `summaryStale` 메타로 신뢰성 시그널을 명확히 전달할 수 있다.
