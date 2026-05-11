# 시스템 아키텍처 개요

## 멀티모듈 헥사고날

```
alt/
├── domain/                      # 순수 자바, Spring/JPA 의존성 0 (ArchUnit 강제)
│   ├── member/                  # Member, MemberId, PhoneNumber, SubscriptionStatus
│   ├── channel/                 # Channel, ChannelId, ChannelType
│   ├── subscription/            # SubscriptionAttempt (Aggregate Root, 사가 상태머신)
│   ├── policy/                  # SubscriptionTransitionPolicy
│   └── error/                   # ErrorCode, DomainException 계층
│
├── application/                 # UseCase + Port (in/out) + Service 흐름 조립
│   └── subscription/
│       ├── port/in/             # 3개 UseCase (Subscribe, Unsubscribe, QueryHistory)
│       ├── port/out/            # MemberCommand/QueryPort, ChannelQueryPort,
│       │                        # SubscriptionAttempt*Port, RandomClient,
│       │                        # LlmSummaryClient, IdempotencyRegistryPort
│       ├── service/             # UseCase 진입점
│       ├── coordinator/         # 트랜잭션 경계 (Saga 단계별 짧은 TX)
│       ├── facade/              # 한 TX 내 여러 Port 묶음 호출
│       ├── factory/             # Command → 도메인 Bundle 변환
│       └── manager/             # 단일 Port 호출 + 도메인 검증 위임
│
├── adapter-in/                  # REST 컨트롤러 (driving adapter)
│
├── adapter-out/
│   ├── persistence-mysql/       # JPA Entity, Mapper, QueryDSL, Adapter, Flyway
│   ├── cache-caffeine/          # 멱등성 게이트
│   ├── client-csrng/            # csrng RestClient + Resilience4j
│   └── client-llm/              # OpenAI RestClient + NoOp fallback
│
└── bootstrap/                   # 진입점 모듈 그룹 (다중 진입점 지원, ADR-0005)
    └── bootstrap-api/           # REST API 진입점 (@SpringBootApplication)
                                 # 향후 bootstrap-scheduler / bootstrap-worker 등 추가
```

> 각 어댑터는 자기 책임의 yml(`web.yml`, `persistence.yml`, `csrng-client.yml` …)을 자기 모듈에 들고 있다. bootstrap의 `application.yml`이 `spring.config.import`로 합쳐 옴 → **어떤 어댑터를 implementation으로 받느냐가 그 진입점의 활성 모듈/설정/엔드포인트를 결정**한다.

## 의존 방향 (단방향, ArchUnit 강제)

```
bootstrap → adapter-{in,out} → application → domain
```

- adapter끼리 서로 의존 금지.
- domain 모듈은 `org.springframework.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.*` 임포트 금지.
- application은 도메인만 알고, 어떤 어댑터(MySQL/Redis/OpenAI/Anthropic/Bedrock)가 뒤에 있는지 모른다.

## 도메인 모델

### Aggregate

| Aggregate | 핵심 책임 |
|---|---|
| **Member** | 휴대폰번호 + 현재 구독 상태(SubscriptionStatus). 도메인 정책으로 전이 검증. |
| **Channel** | 채널 타입(BOTH / SUBSCRIBE_ONLY / UNSUBSCRIBE_ONLY). `canSubscribe()` / `canUnsubscribe()`를 직접 노출. |
| **SubscriptionAttempt** | 사가 상태 머신. 한 시도 = 한 row. PENDING → 종착 상태(COMMITTED / ROLLED_BACK / FAILED) 전이는 도메인 메서드로만. |
| **HistorySummary** | 회원 단위 LLM 요약 영속체 — DB 가 단일 source-of-truth. fingerprint 가 현재 이력과 일치하면 LLM 호출 스킵 (불일치/stale 시에만 재생성). |

### 상태 전이

**구독 상태** — `SubscriptionTransitionPolicy`:
- 구독: `NONE → {BASIC, PREMIUM}`, `BASIC → PREMIUM`
- 해지: `PREMIUM → {BASIC, NONE}`, `BASIC → NONE`
- 위반 시 `InvalidSubscribeTransitionException` / `InvalidUnsubscribeTransitionException` (HTTP 422).

**시도 상태** — `SubscriptionAttempt`:
```
PENDING ──commit────→ COMMITTED   (terminal)
        ──rollback──→ ROLLED_BACK (csrng random=0)
        ──fail──────→ FAILED      (csrng 호출 자체 실패)
```
terminal 진입 후 변경 불가 (`ensurePending()`로 강제).

### LoD 준수

2단계 getter 체이닝 금지. 객체가 자체 메서드를 노출.
```java
// 금지
member.status().canSubscribeTo(target)
channel.id().value()

// 허용
member.canSubscribeTo(target)
channel.idValue()
```

## Application 레이어 분해

본 프로젝트는 단순 `Service` 한 클래스가 아니라 책임이 분리되어 있다.

| 컴포넌트 | 책임 | 트랜잭션 |
|---|---|---|
| **Service** | UseCase 진입점. 멱등성 게이트로 본 흐름을 감싸고 흐름만 조립. | 없음 (단계별 TX) |
| **Coordinator** | **트랜잭션 경계 단위**. Saga의 한 단계를 책임. csrng 호출 같은 외부 작업은 코디네이터 안에서 TX 밖으로 분리. | `@Transactional` |
| **Facade** | 한 TX 안에서 여러 영속 Port를 함께 호출할 때 묶음. | 호출자 TX 따름 |
| **Manager** | 단일 Port 호출 + 도메인 검증 위임. Service에서 Port를 직접 부르지 않게 함. | 없음 |
| **Factory** | Command → 도메인 객체 묶음(`SubscribeBundle`) 변환. | 없음 |

**왜 분해했나**:
1. **트랜잭션 경계와 흐름 조립의 분리** — Saga에서 트랜잭션 경계가 흐름 정의의 일부가 되면 변경 비용이 폭발.
2. **테스트 격리** — 단일 책임이라 단위 테스트가 작고 빠르다.
3. **확장 흡수** — 외부 호출이 동기→비동기로 진화하거나 결제/알림 같은 사이드이펙트가 추가될 때 변경을 흡수.

**트레이드오프**: 클래스 수가 많아 첫 인상이 무겁다. 과제 규모만 보면 과분하지만 "이 코드가 팀에 넘겨져 1년 굴린다"는 가정에서 합리적인 선택.

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

도메인 `ErrorCode` → HTTP status 매핑은 `adapter-in/common/error/SubscriptionErrorMapper`. 코드 일람은 [docs/quickstart.md](../quickstart.md#에러-코드-일람) 참고.
