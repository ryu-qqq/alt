# alt 프로젝트 CLAUDE.md

## 프로젝트 개요
ARTINUS Backend Engineer 과제 — 구독 서비스 백엔드 API.
도메인은 회원의 구독 상태(NONE / BASIC / PREMIUM)와 채널 권한(구독/해지/둘 다)을 다루며, 외부 API(csrng) 호출 결과에 따라 트랜잭션을 처리한다. LLM API로 구독 이력을 자연어로 요약한다.

## 언어 설정
- 모든 응답, 코드 주석, 커밋 메시지, 문서는 **한국어**로 작성한다.
- 코드 내 변수명/메서드명 등 프로그래밍 식별자는 영어를 유지한다.

## 기술 스택
| 항목 | 선택 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Build | Gradle (Kotlin DSL) 멀티모듈, Version Catalog |
| Database | MySQL 8 + Flyway (Testcontainers 통합 테스트) |
| Cache / Lock | Redis (Redisson — 이력 요약 캐시, 분산 락) |
| 회복탄력성 | Resilience4j (CircuitBreaker + Retry + TimeLimiter) |
| LLM | RestClient + OpenAI gpt-4o-mini (Spring AI 미사용 — Port 추상화로 충분) |
| Test | JUnit 5 + AssertJ + Testcontainers + ArchUnit |

---

## 아키텍처 — Hexagonal (Port & Adapter) 멀티모듈

```
alt/
├── domain/                              # 순수 자바, Spring/JPA 의존성 없음 (ArchUnit 강제)
│   ├── member/                          # MemberId, PhoneNumber, SubscriptionStatus, Member
│   ├── channel/                         # ChannelId, ChannelType, Channel
│   ├── subscription/                    # AttemptId, Attempt*, SubscriptionAttempt (사가 상태 머신)
│   ├── policy/                          # SubscriptionTransitionPolicy
│   └── error/                           # ErrorCategory, ErrorCode, SubscriptionErrorCode, DomainException
├── application/                         # UseCase + Port(in/out)
├── adapter-in/                          # REST 컨트롤러, DTO(record), Swagger
├── adapter-out/
│   ├── persistence-mysql/               # JPA Entity(*Entity), Mapper, Adapter, Flyway
│   ├── persistence-redis/               # 이력 요약 캐시
│   ├── client-csrng/                    # RestClient + Resilience4j
│   └── client-llm/                      # RestClient + OpenAI
└── bootstrap/                           # @SpringBootApplication
```

### 설계 원칙
1. **Domain은 순수 자바** — Spring, JPA, Jackson 등 외부 프레임워크 import 금지 (ArchUnit 강제)
2. **Port는 Application에** — Domain은 "내가 뭘 할 수 있는가"만, "외부에 뭘 요청하는가"는 Application의 관심사
3. **Saga + Attempt 상태 머신** — csrng 호출은 트랜잭션 밖에서, SubscriptionAttempt가 모든 시도를 추적 (ADR-0002)
4. **회복탄력성은 어댑터에 캡슐화** — Resilience4j 어노테이션은 어댑터의 메서드에만 적용
5. **트레이드오프 문서화** — 모든 주요 의사결정은 `docs/adr/`에 기록

---

## 참조 문서

| 문서 | 경로 | 설명 |
|------|------|------|
| ADR | `docs/adr/` | 0001 헥사고날, 0002 사가, 0003 도메인 순수성, 0004 멱등성 |
| 아키텍처 | `docs/architecture/` | 시스템 개요, API 데이터 흐름, AWS 배포 설계 |

---

## 핵심 도메인 (단일 BC)

| 영역 | 패키지 | 핵심 |
|------|--------|------|
| Member | `domain/member/` | Member (Aggregate Root), PhoneNumber, SubscriptionStatus |
| Channel | `domain/channel/` | Channel (Aggregate Root), ChannelType |
| Subscription | `domain/subscription/` | SubscriptionAttempt (Aggregate Root) — 사가 상태 머신 |

### 상태 머신 요약

**구독 상태 전이**:
- 구독 (subscribe): NONE → {BASIC, PREMIUM}, BASIC → PREMIUM
- 해지 (unsubscribe): PREMIUM → {BASIC, NONE}, BASIC → NONE

**시도 상태 전이**:
- PENDING → {COMMITTED, ROLLED_BACK, FAILED} (terminal 후 변경 불가)

---

## 외부 API 회복탄력성 정책 (csrng / LLM)

- **TimeLimiter**: 호출 자체에 짧은 timeout (예: 2s)
- **Retry**: 5xx/네트워크 오류 한정, 지수 백오프 (예: 100ms × 2)
- **CircuitBreaker**: 일정 실패율 초과 시 OPEN → 빠른 실패 (FAILED 처리)
- **Bulkhead**: 외부 API 호출 동시성 제한
- 어댑터 메서드에 `@CircuitBreaker`, `@Retry`, `@TimeLimiter` 어노테이션을 직접 부여

---

## 테스트 전략
1. **단위 테스트**: 도메인 모델 비즈니스 로직 (순수 자바, 외부 의존 없음)
2. **ArchUnit 테스트**: domain 레이어 의존성 강제 (Spring/JPA import 금지, Setter 금지, public 생성자 금지 등)
3. **통합 테스트**: Testcontainers(MySQL, Redis) 기반 어댑터 검증
4. **외부 API 카오스 테스트**: csrng 어댑터를 fake로 교체해 timeout / 5xx / circuit open 시나리오 검증
5. **API 테스트**: MockMvc 기반 컨트롤러 테스트

---

## 도메인 LoD 규칙 (강제)

객체가 자체 메서드를 직접 노출한다. 2단계 getter 체이닝 금지.

```java
// 금지
member.status().canSubscribeTo(target)     // 2단계 체이닝
channel.type().canSubscribe()              // 2단계 체이닝
channel.id().value()                       // 2단계 체이닝

// 허용
member.canSubscribeTo(target)              // 1단계
channel.canSubscribe()                     // 1단계
channel.idValue()                          // raw value accessor
```

---

## AI 에이전트 (`.claude/agents/`)

ota-toy 프로젝트에서 가져온 멀티 에이전트 하네스. 핵심 에이전트:

| 역할 | 에이전트 | 용도 |
|------|---------|-----|
| 컨벤션 수호 | `convention-guardian` | ArchUnit 테스트 작성/수정 (유일 수정자) |
| 컨벤션 조사 | `convention-advocate` | 컨벤션 이의 조사 |
| 도메인 개발 | `domain-builder` | 도메인 모델 생성 |
| 도메인 코드리뷰 | `domain-code-reviewer` | 컨벤션/구조 검증 |
| Application 개발 | `application-builder` | UseCase, Port, Manager, Validator |
| Persistence 개발 | `persistence-mysql-builder` | JPA Entity, Mapper, Adapter |
| API 개발 | `rest-api-builder` | Controller, Request DTO, Swagger |
| 의존성 수호 | `dependency-guardian` | build.gradle.kts / libs.versions.toml 유일 수정자 |

(나머지 에이전트는 ota-toy 도메인에 특화되어 있을 수 있다 — 사용 시 alt 도메인에 맞춰 컨텍스트 갱신 필요)

---

## 커밋 컨벤션
```
feat: 새로운 기능 추가
fix: 버그 수정
docs: 문서 변경
refactor: 리팩토링
test: 테스트 추가/수정
chore: 빌드, 설정 변경
```

## 주의사항
- 코드 작성 전 반드시 해당 레이어 컨벤션 문서 참조
- ArchUnit 테스트 파일은 convention-guardian만 수정
- 도메인은 LoD 위반(`a.b().c()` 2단계 체이닝)을 허용하지 않는다 — 객체가 자체 메서드를 노출
- 설계 문서에 적은 내용이 실제 코드에 반영되어야 함
