---
name: persistence-harness
description: |
  Persistence 레이어 전용 하네스. 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 강제 실행한다.
  "persistence 하네스", "persistence 파이프라인", "persistence harness", "persistence 빌드",
  "persistence 리뷰 돌려줘", "persistence 검증", "Entity 빌드", "Adapter 검증",
  "영속성 빌드", "영속성 검증" 등의 요청에 사용한다.
  Persistence 코드를 만들거나, 기존 Persistence 코드를 검증할 때 반드시 이 하네스를 통해 실행한다.
---

# Persistence 하네스

## 개요

Persistence 레이어의 코드 품질을 **파이프라인으로 강제**하는 실행 하네스.
"만든다 → 검증한다 → 고친다 → 테스트한다"를 빠짐없이 수행한다.

---

## 실행 모드

### 모드 1: 빌드 (`/persistence-harness build {대상}`)
신규 Persistence 코드를 생성하고 전체 파이프라인을 돌린다.

예시:
```
/persistence-harness build STORY-104
```

### 모드 2: 리뷰 (`/persistence-harness review {대상}`)
기존 Persistence 코드를 검증하고, FIX가 필요하면 수정 루프를 돌린다.

### 모드 3: 테스트 (`/persistence-harness test {대상}`)
Persistence 테스트만 작성/실행한다.

---

## 대상 단위

Application 하네스와 동일하게 **Story 단위**로 실행한다.

---

## 실행 시 이 스킬이 하는 것

1. 사용자 커맨드를 파싱한다 (모드 + 대상 Story)
2. `persistence-harness-orchestrator` 에이전트를 호출한다
3. 에이전트가 반환하는 중간 결과를 사용자에게 보고한다
4. ESCALATION이 발생하면 사용자에게 선택지를 제시한다
5. 최종 결과를 요약하여 보고한다

---

## 사용자 인터랙션

### 정상 흐름
```
사용자: /persistence-harness build STORY-104

스킬: "STORY-104 Persistence 빌드 파이프라인을 시작합니다."
      → persistence-harness-orchestrator 호출

[Phase 0] 전제조건 확인
  → convention-03-persistence.md ✅
  → ERD ✅
  → Application Port 인터페이스 존재 ✅
  → 도메인 코드 존재 ✅

[Phase 1] persistence-mysql-builder 실행
  → Flyway 마이그레이션, Entity, Mapper, Repository, Adapter 생성
  → 컴파일: ✅

[Phase 2] 컨벤션 셀프 체크
  → Entity 관계 어노테이션 없음 ✅
  → Lombok 없음 ✅
  → create() 팩토리 패턴 ✅
  → CQRS 분리 ✅

[Phase 3] persistence-mysql-test-designer 실행
  → Testcontainers 기반 통합 테스트 작성
  → 실행: 12/12 통과 ✅

[Phase 4] 결과 문서화

"STORY-104 Persistence 빌드 파이프라인 완료."
```

---

## 에이전트 호출 프롬프트 템플릿

이 스킬은 `persistence-harness-orchestrator` 에이전트를 호출할 때 아래 정보를 전달한다:

```
모드: {build | review | test}
대상: {Story 번호 또는 all}
컨벤션: docs/design/convention-03-persistence.md
구현 가이드: docs/design/phase2-implementation-guide.md
ERD: docs/erDiagram.md
Application Port: application/src/main/java/com/ryuqq/otatoy/application/{bc}/port/out/
Domain 코드: domain/src/main/java/com/ryuqq/otatoy/domain/
Persistence 코드: adapter-out/persistence-mysql/src/main/java/
```
