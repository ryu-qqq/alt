---
name: persistence-harness-orchestrator
description: Persistence 레이어 하네스 실행 엔진. persistence-harness 스킬에서만 호출된다. 에이전트 호출 순서, FIX 루프, 에스컬레이션을 관리한다.
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
---

# Persistence Harness Orchestrator

## 역할
Persistence 레이어의 빌드 → 검증 → 수정 → 테스트 파이프라인을 **강제 실행**하는 엔진.

**핵심 원칙**: 테스트를 건너뛸 수 없다. builder가 만든 코드는 반드시 컨벤션 검증과 통합 테스트를 거친다.

---

## 실행 흐름

### 모드: build

```
Phase 0: 전제조건 확인
  → docs/design/convention-03-persistence.md 존재 확인
  → docs/erDiagram.md 존재 확인
  → 대상 Story의 Application Port 인터페이스 존재 확인
  → 대상 도메인 코드 존재 확인
  → 기존 Persistence 코드 확인 (중복 방지)
  → 기존 Flyway 마이그레이션 버전 확인

Phase 1: persistence-mysql-builder 호출
  → Flyway 마이그레이션 SQL 작성
  → JPA Entity 작성 (BaseAuditEntity/SoftDeletableEntity 상속)
  → Mapper 작성 (Domain ↔ Entity)
  → JpaRepository 작성
  → QueryDslRepository 작성 (필요 시)
  → Adapter 작성 (Port 구현)
  → 컴파일 확인 (./gradlew :adapter-out:persistence-mysql:compileJava)
  → 매니페스트 수집

Phase 2: 컨벤션 셀프 체크 (orchestrator가 직접 수행)
  → PER-ENT-001: JPA 관계 어노테이션 금지 (grep @OneToMany, @ManyToOne 등)
  → PER-ENT-004: Lombok 금지 (grep @Getter, @Setter, @NoArgsConstructor 등)
  → PER-ENT-005: Entity에 비즈니스 로직 금지 (update*, change* 메서드 없음)
  → PER-ENT-001: create() 팩토리 메서드 존재
  → PER-REP-001: JpaRepository에 커스텀 메서드 금지 (@Query, findBy* 없음)
  → PER-ADP-001: CQRS 분리 (CommandAdapter는 JpaRepository만, QueryAdapter는 QueryDslRepository만)
  → PER-MAP-001: Mapper에서 Domain.reconstitute() 사용 확인
  → FAIL 시 → FIX-REQUEST를 builder에게 전달 → FIX 루프 (최대 2회)

Phase 3: persistence-mysql-test-designer 호출
  → Testcontainers MySQL 기반 통합 테스트 작성
    - PT-1: Entity ↔ Domain 매핑 정합성
    - PT-2: CRUD 동작 검증
    - PT-3: QueryDSL 커스텀 쿼리 (있으면)
    - PT-4: Flyway 마이그레이션 정상 적용
  → 테스트 실행 (./gradlew :adapter-out:persistence-mysql:test)
  → 실패 시 FIX 루프 (최대 2회)

Phase 4: 결과 문서화 + 완료 보고
  → docs/review/{story}-persistence-review.md
  → docs/review/{story}-persistence-test-scenarios.md
  → docs/review/{story}-persistence-harness-result.md
```

### 모드: review

Phase 1을 건너뛰고 Phase 2부터 시작.

```
Phase 0: 전제조건 확인
Phase 2: 컨벤션 셀프 체크
Phase 3: 테스트
Phase 4: 결과 문서화
```

### 모드: test

Phase 3만 실행.

```
Phase 0: 전제조건 확인
Phase 3: 테스트 작성 + 실행
Phase 4: 결과 문서화
```

---

## 에이전트 호출 규칙

### persistence-mysql-builder 호출 시
```
에이전트: .claude/agents/persistence-mysql-builder.md
프롬프트에 포함:
  - 대상 Story 번호 + 수용기준
  - 구현 가이드 참조
  - Persistence 컨벤션 참조
  - ERD 참조
  - Application Port 인터페이스 경로
  - 도메인 코드 경로
  - 기존 Flyway 마이그레이션 버전
  - FIX-REQUEST 목록 (FIX 루프 시)
  - "컴파일 확인 후 매니페스트 출력"
```

### persistence-mysql-test-designer 호출 시
```
에이전트: .claude/agents/persistence-mysql-test-designer.md
프롬프트에 포함:
  - builder 매니페스트 (생성된 파일 목록)
  - 컨벤션 참조
  - 기존 테스트 파일 (중복 방지)
  - "테스트 작성 후 실행, 결과 반환"
```

---

## Phase 2: 컨벤션 셀프 체크 상세

별도 reviewer 에이전트가 없으므로 orchestrator가 직접 grep/read로 검증한다.

### 검증 항목

| 규칙 | 검증 방법 | 심각도 |
|------|----------|--------|
| PER-ENT-001: @OneToMany/@ManyToOne 금지 | grep `@OneToMany\|@ManyToOne\|@OneToOne\|@ManyToMany` | BLOCKER |
| PER-ENT-004: Lombok 금지 | grep `@Getter\|@Setter\|@NoArgsConstructor\|@AllArgsConstructor\|@Builder\|@Data` | BLOCKER |
| PER-ENT-001: create() 팩토리 존재 | grep `public static.*create\(` | BLOCKER |
| PER-ENT-005: 비즈니스 로직 금지 | grep `public.*update\|public.*change\|public.*activate\|public.*deactivate` (Entity에서) | MAJOR |
| PER-ENT-001: setter 금지 | grep `public void set` (Entity에서) | BLOCKER |
| PER-REP-001: JpaRepository 커스텀 메서드 금지 | grep `@Query\|findBy\|deleteBy\|countBy` (JpaRepository에서) | MAJOR |
| PER-ADP-001: CQRS 분리 | CommandAdapter는 JpaRepository만, QueryAdapter는 QueryDslRepository만 의존 확인 | MAJOR |
| PER-MAP-001: reconstitute() 사용 | Mapper의 toDomain에서 reconstitute 호출 확인 | MAJOR |
| PER-FLY-001: Flyway 파일 존재 | 마이그레이션 파일이 ERD와 일치하는지 확인 | BLOCKER |

---

## FIX 루프 관리

```
컨벤션 FIX 루프: 최대 2회
테스트 FIX 루프: 최대 2회
```

---

## ESCALATION

FIX 루프가 최대 횟수를 초과하면:

1. 미해결 이슈 목록 정리
2. 선택지 제시
3. 사용자에게 결정 요청
4. 결정을 builder에게 전달

---

## 상태 보고

```
[Phase 0] 전제조건: ✅ 모두 충족
[Phase 1] builder: Flyway 2개, Entity 4개, Mapper 4개, Adapter 4개 생성, 컴파일 ✅
[Phase 2] 컨벤션 셀프 체크: 9/9 통과 ✅
[Phase 3] 테스트: 12/12 통과 ✅ (Testcontainers MySQL)
[Phase 4] 문서화 완료
[완료] STORY-104 Persistence 파이프라인 통과
```

---

## Domain/Application 하네스와의 차이점

| 항목 | Domain | Application | Persistence |
|------|--------|------------|-------------|
| 대상 단위 | BC | Story | Story |
| 리뷰어 | code + spec (2명) | reviewer (1명) | 셀프 체크 (grep) |
| 테스트 유형 | 단위 (순수 Java) | 단위 (Mockito) | 통합 (Testcontainers) |
| FIX 루프 | 리뷰 3 / 테스트 2 | 리뷰 2 / 테스트 2 | 셀프 2 / 테스트 2 |
| 추가 산출물 | — | — | Flyway SQL |

---

## 주의사항

- **테스트를 건너뛸 수 없다.** Persistence는 통합 테스트가 핵심이다.
- **Flyway 마이그레이션과 Entity가 일치해야 한다.** 불일치는 BLOCKER.
- **JPA 관계 어노테이션은 절대 금지.** Long FK 전략만 사용한다.
- **Entity에 비즈니스 로직 금지.** create(), getter만 허용.
- **builder가 만든 코드도, 사람이 만든 코드도** 동일한 파이프라인을 거친다.
