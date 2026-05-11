# ADR-0001. 헥사고날 아키텍처 + Gradle 멀티모듈 채택

## Context

ARTINUS 백엔드 과제는 5개 평가 항목을 동시에 검증한다 — 아키텍처/요구사항/API/외부 API 장애 대응/클라우드 인프라.

특히 다음 두 요인이 구조 결정의 핵심이다:

1. **외부 API 변동성** — csrng는 random 결과를 반환하는 외부 API이고, LLM은 OpenAI를 쓰지만 Anthropic / Bedrock으로 바뀔 수 있다. 외부 변동이 도메인 규칙을 흔들면 안 된다.
2. **장애 격리 가시성** — csrng 장애가 멤버 도메인이나 이력 도메인의 코드 변경을 유발하면 안 된다. 어댑터에서 격리되어야 한다.

## 검토한 대안

### Option A — 단일 모듈 + 패키지 기반 layered (`controller/service/repository`)

- 가장 단순. 작은 과제에 어울림.
- 단점:
  - 외부 의존(JPA/RestClient)이 service에 침투하기 쉬움.
  - 의존 방향을 컴파일러가 강제하지 못함 — 시간이 지나면 컨트롤러가 repository를 직접 호출하는 식으로 무너짐.
  - csrng/LLM 클라이언트 변경이 service 코드 수정으로 직결.

### Option B — 단일 모듈 + 패키지 기반 헥사고날

- domain/application/adapter를 패키지로 분리.
- 단점:
  - 패키지 분리만으로는 의존 방향이 강제되지 않음. ArchUnit으로 보강 가능하지만 모듈 분리만큼 강하지는 않다.
  - 도메인 모듈에 의도치 않은 Spring 의존성 흡수 (build.gradle이 단일이라 starter들이 다 따라옴).
  - 서비스 진화 시 (예: bootstrap을 batch와 분리) 모듈로 다시 쪼개는 비용이 큼.

### Option C — Gradle 멀티모듈 헥사고날 (채택)

- domain / application / adapter-{in,out,*} / bootstrap을 별도 Gradle 모듈로 분리.
- 의존성을 **build.gradle에서 물리적으로** 통제.

## Decision

**Option C 채택.** 헥사고날(Port & Adapter)을 Gradle 멀티모듈로 분리한다.

```
alt/
├── domain/                      # 순수 자바, Spring 의존성 없음 (ADR-0003)
├── application/                 # UseCase + Port 정의
├── adapter-in/                  # REST 컨트롤러 (driving adapter)
├── adapter-out/
│   ├── persistence-mysql/       # JPA + QueryDSL + Flyway
│   ├── cache-caffeine/          # 멱등성 게이트
│   ├── client-csrng/            # RestClient + Resilience4j
│   └── client-llm/              # RestClient + OpenAI
└── bootstrap/                   # 진입점 모듈 그룹 (ADR-0005)
    └── bootstrap-api/           # REST API 진입점 — 향후 scheduler/worker 등 추가
```

의존 방향: `bootstrap → adapter → application → domain` (단방향). adapter끼리는 서로 의존하지 않는다.

## 선택 근거

- **컴파일러가 의존 방향을 강제** — domain 모듈이 Spring을 의존하지 않으면 빌드 자체가 안 된다. 코드 리뷰에 의존하지 않고 구조가 강제된다.
- **외부 API 교체가 어댑터 모듈 교체로 끝남** — csrng → 자체 random 소스, OpenAI → Anthropic / Bedrock 같은 변경이 application/domain 코드 수정 없이 가능. 신규 LLM provider 추가 시 `adapter-out/client-anthropic` 같은 모듈 신설 + bootstrap 의존만 교체.
- **테스트 격리** — domain 모듈은 Spring Context 없이 단위 테스트가 가능해 매우 빠르다 (밀리초 수준).
- **진화 친화** — bootstrap을 분리해두면 batch / 별도 워커 / API gateway 같은 진입점 추가 시 어댑터 재사용 가능.
- **평가 항목 1번 (아키텍처) 직접 충족** — 시니어 평가에서 "구조에 대한 의도적 결정"을 가시적으로 보여준다.

## 장점

- (+) 외부 API/저장소 변경 비용이 어댑터 모듈에 국한.
- (+) ArchUnit으로 의존 방향과 패키지 침범 금지를 자동 검증 ([ADR-0003](0003-domain-purity.md) 참고).
- (+) bootstrap을 분리해 향후 진입점 다양화에 대비.
- (+) 멱등성 어댑터(`cache-caffeine`)를 별도 모듈로 둬, 분산 환경 진화 시 `cache-redis`로 모듈 단위 교체 가능 ([ADR-0004](0004-idempotency-strategy.md)).

## 단점 / 비용

- (-) 단순 CRUD 대비 모듈 수가 많아 초기 셋업 비용 발생 (build.gradle.kts × 8개, settings.gradle.kts 관리).
- (-) 새 기능 추가 시 여러 모듈을 동시에 건드리게 되어 PR 단위가 커질 수 있음.
- (-) 신규 합류자에게 학습 곡선이 있음 — 다만 의존 방향이 강제되어 있어 잘못된 코드를 작성하기 어렵다는 보호막이 있다.

## 평가

본 과제 규모만 놓고 보면 Option A로도 요구사항 충족은 가능하다. 그러나 평가 항목 1번이 "아키텍처 설계 및 프로젝트 구성"으로 명시되어 있고, 시니어(5~8년) 채용 과제라는 컨텍스트에서 **구조에 대한 의도성을 명시적으로 보여주는 가치**가 모듈 셋업 비용보다 크다고 판단했다.
