# ADR-0001. 헥사고날 아키텍처 + Gradle 멀티모듈 채택

- Status: Accepted
- Date: 2026-05-08

## Context

ARTINUS 백엔드 과제는 5개 평가 항목을 동시에 검증한다.

1. 아키텍처 설계 및 프로젝트 구성
2. 요구사항 이해
3. API 설계 및 구현
4. 외부 API 장애 대응 (csrng)
5. 클라우드 인프라 설계

특히 csrng 외부 API 장애 처리와 LLM 연동을 어댑터 단위로 격리해, 외부 변동(csrng → 다른 random 소스, Anthropic ↔ OpenAI ↔ Bedrock)이 도메인 규칙을 흔들지 않도록 해야 한다.

## Decision

헥사고날(Port & Adapter) 아키텍처를 Gradle 멀티모듈로 **물리적으로** 분리한다.

```
alt/
├── domain/                              # 순수 자바, Spring 의존성 없음 (ADR-0003)
├── application/                         # UseCase + Port 정의
├── adapter-in/                          # REST 컨트롤러 (driving adapter)
├── adapter-out/
│   ├── persistence-mysql/               # JPA + QueryDSL + Flyway
│   ├── persistence-redis/               # Redisson (캐시/분산락)
│   ├── client-csrng/                    # RestClient + Resilience4j
│   └── client-llm/                      # RestClient + Anthropic
└── bootstrap/                           # @SpringBootApplication, 환경 설정 통합
```

의존 방향: `bootstrap → adapter → application → domain` (단방향). adapter끼리는 서로 의존하지 않는다.

## Consequences

- (+) 외부 API 변경 시 어댑터 모듈만 교체. 도메인/애플리케이션 코드 무영향.
- (+) ArchUnit으로 의존 방향과 패키지 침범 금지를 강제 가능 (별도 작업).
- (+) bootstrap을 분리해 향후 Web/Batch 등 진입점이 달라져도 어댑터 재사용 가능.
- (-) 단순 CRUD 대비 모듈 수가 많아 초기 비용 발생. 다만 평가 항목 1번을 명시적으로 충족.
