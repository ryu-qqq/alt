# ADR-0005. bootstrap을 진입점별 sub-module로 분리

## Context

[ADR-0001](0001-hexagonal-architecture.md)에서 헥사고날 + Gradle 멀티모듈을 채택했다. 그때는 `bootstrap` 단일 모듈이었다.

운영 시점에 다음 진입점들이 필요해질 가능성이 높다:

- **API**: 동기 REST 트래픽 처리. ALB 뒤. 트래픽에 따라 빠른 스케일 인/아웃.
- **Scheduler**: 주기 잡 — 예: PENDING 좀비 attempt 정리, LLM 요약 캐시 무효화. 단일 인스턴스 충분, ALB 불필요.
- **Worker**: 비동기 작업 — 예: SQS/Kafka 컨슈머로 LLM 요약 백그라운드 생성. 트래픽 패턴이 API와 다름.

세 진입점 모두 같은 `domain` / `application` / 일부 `adapter-out`을 공유하지만:

- 컨테이너/스케일링 단위는 분리되어야 함 (트래픽 특성/SLA 다름).
- 활성화될 어댑터 조합이 다름 (Worker는 `adapter-in/web` 불필요).
- 시크릿/설정 노출 범위도 다름 (Scheduler는 LLM API key 불필요할 수도).

## 검토한 대안

### Option A — bootstrap 단일 모듈 + Spring Profile로 분기

```
java -jar bootstrap.jar --spring.profiles.active=api
java -jar bootstrap.jar --spring.profiles.active=scheduler
```

- 장점: 모듈 셋업 비용 0. jar 하나로 운영.
- 단점:
  - **모든 진입점이 모든 의존성을 들고 다님** — Worker가 `adapter-in/web`까지 빌드 산출물에 포함. 이미지 크기 증가.
  - 한 진입점에서만 쓰는 라이브러리 취약점이 다른 진입점의 보안 스코프에 들어옴.
  - "이 진입점에서 활성화 안 됐겠지" 가정에 의존 — Spring Profile 누락 시 의도치 않은 빈이 뜸.
  - 스케일링/배포는 분리할 수 있지만 빌드/이미지는 일체형.

### Option B — 진입점별 별도 Git 레포

- 진입점마다 독립 빌드.
- 단점:
  - domain / application 변경 시 N개 레포에 동기화. 모노레포의 장점 상실.
  - 의존 모듈 버전 드리프트 위험.

### Option C — bootstrap을 sub-module로 분리 (채택)

```
bootstrap/
├── bootstrap-api/        # implementation: domain, application, adapter-in, adapter-out/*
├── bootstrap-scheduler/  # implementation: domain, application, adapter-out/persistence-mysql (예시)
└── bootstrap-worker/     # implementation: domain, application, adapter-out/{persistence,client-llm} (예시)
```

각 진입점은 자기에게 필요한 모듈만 implementation으로 받는다.

## Decision

**Option C 채택.** bootstrap을 진입점별 sub-module로 분리한다.

### 현재 구조

```
bootstrap/
└── bootstrap-api/                  # 현재 유일 진입점
    ├── ApiApplication
    ├── application.yml             # spring.config.import로 어댑터 yml 합침
    └── application-local.yml
```

### 향후 추가 시 패턴

```
bootstrap/
├── bootstrap-api/
├── bootstrap-scheduler/            # @EnableScheduling
└── bootstrap-worker/               # SQS/Kafka 리스너
```

### 핵심 설계 — 어댑터 자기 yml 보유

각 어댑터가 **자기 책임의 yml을 자기 모듈 src/main/resources에 둠**:

| 어댑터 | yml | 내용 |
|---|---|---|
| `adapter-in` | `web.yml` | server.port, MVC, Swagger |
| `adapter-out/persistence-mysql` | `persistence.yml` | datasource, JPA, Flyway |
| `adapter-out/cache-caffeine` | `cache-caffeine.yml` | 멱등성 캐시 TTL |
| `adapter-out/client-csrng` | `csrng-client.yml` | base-url, Resilience4j 정책 |
| `adapter-out/client-llm` | `llm-client.yml` | base-url, OpenAI 모델(gpt-4o-mini), Resilience4j |

진입점은 `application.yml`에서 필요한 yml만 import:

```yaml
# bootstrap-api/application.yml
spring:
  config:
    import:
      - classpath:web.yml
      - classpath:persistence.yml
      - classpath:cache-caffeine.yml
      - classpath:csrng-client.yml
      - classpath:llm-client.yml
```

→ 어떤 어댑터를 implementation으로 받느냐가 그 진입점의 활성 모듈/설정/엔드포인트를 결정.

## 선택 근거

- **빌드 산출물 최소화** — 진입점별 jar/이미지가 자기 어댑터만 포함. Worker 이미지에 Tomcat이 들어가지 않음.
- **운영 단위 분리** — 진입점별 ECR 이미지 / ECS Service / Auto Scaling 정책 / IAM Task Role / Secrets 스코프.
- **장애 격리** — API 트래픽 폭증이 Scheduler/Worker에 영향 없음. 외부 API 장애가 Worker만 죽이고 API는 살아 있음.
- **컨벤션 강제** — Worker가 `adapter-in/web`을 빌드하려 해도 안 됨. 의도치 않은 빈이 뜨는 사고 차단.
- **모노레포 유지** — domain/application 변경이 모든 진입점에 일관되게 반영. Option B의 동기화 비용 회피.

## 장점

- (+) 진입점별 독립 배포 (이미지/스케일링/IAM Role 분리).
- (+) 진입점별 보안 스코프 명확 (API key가 필요한 진입점에만 시크릿 노출).
- (+) 빌드 산출물 작음 → 컨테이너 콜드 스타트 빠름.
- (+) 어댑터 yml이 자기 모듈에 있어 어댑터 교체/추가가 진입점 코드 변경 없이 가능.
- (+) ArchUnit으로 "이 진입점에서 이 어댑터 안 쓴다"를 강제 가능.

## 단점 / 비용

- (-) 신규 진입점 추가 시 `bootstrap/<name>/build.gradle.kts` + `Application.java` + `application.yml` 셋업 필요.
- (-) 공통 설정(예: 로깅 레벨)을 진입점마다 중복 둘 위험. 완화: 공통 설정을 별도 모듈/공유 yml로 빼고 import.
- (-) 진입점이 많아지면 settings.gradle.kts include 목록이 길어짐.
- (-) 멀티 진입점 통합 테스트 시나리오 (예: API가 발행한 메시지를 Worker가 처리) 가 별도 설계 필요.

## 평가

본 과제는 진입점이 API 하나뿐이라 Option A로도 동작한다. 그러나 시니어 평가에서 **"향후 진입점이 늘 때 어떻게 진화시킬 것인가"** 에 대한 답이 코드로 드러나는 게 가치 있다. 셋업 비용이 작고(진입점 추가 시 5분), 운영 단위 분리의 가치가 명확해 본 구조를 채택.

배포 측면 영향은 [docs/architecture/aws-deployment.md](../architecture/aws-deployment.md) 참고.
