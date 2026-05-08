# alt — 구독 서비스 백엔드

ARTINUS Backend Engineer 과제 구현체.

> 본 README는 placeholder 입니다. 도메인 설계, 아키텍처, 외부 API 장애 대응, LLM 연동, AWS 배포 설계 문서가 추후 작성됩니다.

## 기술 스택 (확정)

| 영역 | 선택 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Gradle Kotlin DSL (멀티모듈) |
| ORM | Spring Data JPA + QueryDSL 5.1 |
| RDB | MySQL 8 + Flyway |
| Cache / Lock | Redis (Redisson) |
| 회복탄력성 | Resilience4j (CircuitBreaker + Retry + TimeLimiter) |
| LLM | RestClient + Anthropic Claude (단일 호출, Spring AI 미사용) |
| 트랜잭션 전략 | Saga + Compensation (Attempt 상태 머신) |
| Test | JUnit 5 + AssertJ + Testcontainers + ArchUnit |

## 모듈 구조

```
alt/
├── domain/                      # 순수 자바, Spring 의존 X
├── application/                 # UseCase + Port
├── adapter-in/                  # REST 컨트롤러
├── adapter-out/
│   ├── persistence-mysql/
│   ├── persistence-redis/
│   ├── client-csrng/
│   └── client-llm/
└── bootstrap/                   # @SpringBootApplication, 환경 설정 통합
```
