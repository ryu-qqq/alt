# ADR-0003. domain 모듈 순수성 강제 (Spring/JPA 의존성 금지)

## Context

[ADR-0001](0001-hexagonal-architecture.md)에서 헥사고날을 채택했지만, 헥사고날만으로는 도메인 순수성이 보장되지 않는다. 시간이 지나면 편의를 위해 도메인에 다음과 같은 외부 의존이 흘러들어온다:

- `@Entity`, `@Column` (JPA가 도메인 객체에 직접 매핑)
- `@Service`, `@Component` (Spring DI)
- `@JsonProperty`, `@JsonIgnore` (Jackson 직렬화)

결과:
- 외부 라이브러리 메이저 버전 변경이 도메인을 흔든다.
- 단위 테스트가 무거워진다 (Spring Context 로딩, 영속성 컨텍스트 필요).
- 도메인 모델이 ORM 매핑 / 직렬화 요구에 휘둘린다 (no-args constructor 강제, 필드 가시성 변경 등).

## 검토한 대안

### Option A — 도메인에 Spring/JPA 의존성 자유롭게 허용

- 가장 단순. 단일 객체로 매핑 가능.
- 단점:
  - 도메인 로직과 영속화 관심사가 같은 클래스에 섞임.
  - JPA의 lazy loading, dirty checking 같은 부수효과가 도메인 메서드 호출 결과에 영향을 줌.
  - 단위 테스트가 어려워짐 (의존성 mock 비용 증가).

### Option B — 부분 허용 (예: VO만 record + JPA Embeddable, Aggregate Root는 @Entity)

- 절충안. 일반적으로 채택되는 형태.
- 단점:
  - "어디까지 허용?"의 기준이 모호. 코드 리뷰에서 매번 논쟁.
  - Aggregate Root에 JPA가 침투하면 결국 ORM 제약(no-args, 필드 접근자 등)이 도메인 표현을 제한.
  - 컨벤션은 강제되지 않으면 시간이 지나며 무너짐.

### Option C — 도메인 모듈 의존성 0% (채택)

- domain 모듈의 build.gradle.kts에서 Spring / JPA / Jackson을 명시적으로 의존하지 않음.
- ArchUnit으로 임포트 금지 강제.
- JPA 매핑은 별도 `*JpaEntity` + `EntityMapper`로 분리 (Domain ↔ Entity 변환).

## Decision

**Option C 채택.**

- `domain` Gradle 모듈은 Spring / JPA / Jackson 등 외부 라이브러리 의존성을 **명시적으로** 갖지 않는다 (build.gradle.kts 의존성 비어 있음).
- 도메인 구성 요소 표현 형태:
  - **VO**: `record` (불변, 생성자 검증)
  - **엔티티**: `final class` + private 필드 + 정적 팩토리 메서드(`newMember` / `rehydrate`)
  - **enum**: 상태 머신 메서드를 enum 자체에 캡슐화 (예: `SubscriptionStatus#canSubscribeTo`)
  - **정책 객체**: 정적 메서드 + private 생성자 (예: `SubscriptionTransitionPolicy`)
- ArchUnit 테스트로 강제:
  - `domain` 패키지는 `org.springframework.*`, `jakarta.persistence.*`, `com.fasterxml.jackson.*` 임포트 금지
  - 의존 방향: domain ← application ← adapter
  - public 생성자 금지 (정적 팩토리 강제)
  - Setter 금지 (불변 강제)

## 선택 근거

- **컴파일러 + ArchUnit 2중 강제** — 코드 리뷰에 의존하지 않고 구조적으로 위반 불가.
- **단위 테스트 속도** — 도메인 테스트가 Spring Context 없이 동작해 밀리초 수준. 74개 도메인 테스트가 1초 내 완료.
- **외부 라이브러리 변경 영향 0** — Spring Boot 3 → 4 메이저 변경, JPA → JOOQ 전환 같은 시나리오에서 도메인 코드 무영향.
- **도메인 표현의 자유** — `record`, `sealed`, `final class` 같은 자바 21 표현 도구를 ORM 제약 없이 활용.
- **명시적 영속화 의도** — `rehydrate` 정적 팩토리로 "이건 DB에서 복원되는 객체"라는 의도가 코드에 드러남.

## 장점

- (+) 외부 라이브러리 변경 비용이 도메인에 전파되지 않음.
- (+) 도메인 단위 테스트가 매우 빠름.
- (+) 도메인 모델이 ORM/직렬화 제약 없이 비즈니스 표현에 집중 가능.
- (+) Aggregate Root의 불변성과 캡슐화를 강제 가능.
- (+) ArchUnit이 위반을 즉시 빌드 실패로 잡아냄.

## 단점 / 비용

- (-) JPA 매핑이 별도 `*JpaEntity` 클래스 + `EntityMapper` 변환 코드를 요구. 보일러플레이트 발생.
  - 완화: Mapper는 단순 변환이라 테스트가 쉬움. Domain ↔ Entity 변환 비용은 무시할 수준.
- (-) 신규 합류자가 "왜 한 번 더 변환?"을 학습해야 함.
  - 완화: ADR-0001과 함께 읽으면 의도가 명확.
- (-) 도메인 객체가 직접 JSON 직렬화 안 됨. Application이 응답 DTO로 변환해야 함.
  - 이건 오히려 의도된 결과 — 도메인 표현과 API 표현이 결합되지 않음.

## 평가

Option B(부분 허용)가 일반적이지만, 컨벤션을 강제하지 못하면 시간이 지나면서 결국 Option A로 무너진다. 5~8년차 시니어 코드베이스의 수명을 길게 보면 Option C의 보일러플레이트 비용이 가장 작다.
