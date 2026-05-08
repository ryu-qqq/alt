# ADR-0003. domain 모듈 순수성 강제 (Spring/JPA 의존성 금지)

- Status: Accepted
- Date: 2026-05-08

## Context

헥사고날 채택만으로는 도메인 순수성이 보장되지 않는다. 시간이 지나면 편의를 위해 도메인에 `@Entity`, `@Service`, `@Component`가 흘러들어와 외부 변경에 흔들리고 단위 테스트가 무거워진다.

## Decision

- `domain` Gradle 모듈은 Spring / JPA / Jackson 등 외부 라이브러리 의존성을 **명시적으로** 갖지 않는다 (build.gradle.kts에서 의존성 비어 있음).
- 도메인 구성 요소:
  - **VO**: `record` (불변, 생성자 검증)
  - **엔티티**: `final class` + private 필드 + 정적 팩토리 메서드(`newMember` / `rehydrate`)
  - **enum**: 상태 머신 메서드를 enum 자체에 캡슐화 (예: `SubscriptionStatus#canSubscribeTo`)
  - **정책 객체**: 정적 메서드 + private 생성자 (예: `SubscriptionTransitionPolicy`)
- ArchUnit 테스트로 다음을 강제 (별도 작업):
  - `domain` 패키지는 `org.springframework.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.*` 임포트 금지
  - 의존 방향: domain ← application ← adapter

## Consequences

- (+) 도메인 변경 비용이 외부 라이브러리 변경에 영향받지 않음.
- (+) 도메인 단위 테스트가 매우 빠름 (Spring Context 불필요).
- (+) `rehydrate` 정적 팩토리로 영속화 어댑터의 매핑 의도가 명확.
- (-) JPA 매핑은 별도 엔티티 클래스를 `adapter-out/persistence-mysql`에 두고 도메인 객체로 변환하는 코드가 발생.
  - 변환 비용은 작지만 평가 항목 1번 "아키텍처 설계"의 의도성을 명확히 하는 가치가 더 크다.
