---
name: application-harness
description: |
  Application 레이어 전용 하네스. 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 강제 실행한다.
  "application 하네스", "application 파이프라인", "application harness", "application 빌드",
  "application 리뷰 돌려줘", "application 검증", "UseCase 빌드", "UseCase 검증" 등의 요청에 사용한다.
  Application 코드를 만들거나, 기존 Application 코드를 검증할 때 반드시 이 하네스를 통해 실행한다.
---

# Application 하네스

## 개요

Application 레이어의 코드 품질을 **파이프라인으로 강제**하는 실행 하네스.
"만든다 → 리뷰한다 → 고친다 → 테스트한다"를 빠짐없이 수행하고, 각 단계의 결과를 다음 단계에 전달한다.

**문제**: builder에게 코드 생성을 시키고 리뷰/테스트 없이 바로 커밋하면 품질이 보장되지 않는다.
**해결**: 이 하네스가 전체 흐름을 오케스트레이션하여 리뷰와 테스트를 건너뛸 수 없게 한다.

---

## 실행 모드

### 모드 1: 빌드 (`/application-harness build {대상}`)
신규 Application 코드를 생성하고 전체 파이프라인을 돌린다.

예시:
```
/application-harness build STORY-103
/application-harness build STORY-103a
```

### 모드 2: 리뷰 (`/application-harness review {대상}`)
기존 Application 코드를 리뷰하고, FIX가 필요하면 수정 루프를 돌린다.

예시:
```
/application-harness review STORY-103
/application-harness review all
```

### 모드 3: 테스트 (`/application-harness test {대상}`)
Application 테스트만 작성/실행한다 (코드는 이미 있다고 가정).

예시:
```
/application-harness test STORY-103
/application-harness test all
```

---

## 대상 단위

Domain 하네스는 BC(Bounded Context) 단위로 실행하지만, Application 하네스는 **Story(UseCase) 단위**로 실행한다.

이유: Application 레이어는 UseCase 단위로 파일이 생성되고 리뷰된다. 하나의 Story가 하나의 UseCase에 대응한다.

---

## 실행 시 이 스킬이 하는 것

1. 사용자 커맨드를 파싱한다 (모드 + 대상 Story)
2. `application-harness-orchestrator` 에이전트를 호출한다
3. 에이전트가 반환하는 중간 결과를 사용자에게 보고한다
4. ESCALATION이 발생하면 사용자에게 선택지를 제시한다
5. 최종 결과를 요약하여 보고한다

---

## 사용자 인터랙션

### 정상 흐름
```
사용자: /application-harness build STORY-103

스킬: "STORY-103 Application 빌드 파이프라인을 시작합니다."
      → application-harness-orchestrator 호출

[Phase 0] 전제조건 확인
  → convention-02-application.md ✅
  → phase2-implementation-guide.md ✅
  → domain/accommodation/ 코드 존재 ✅

[Phase 1] application-builder 실행
  → UseCase 1개, Port 2개, Command 1개, Manager 2개, Validator 1개, Factory 1개, Service 1개 생성
  → 컴파일: ✅

[Phase 2] application-reviewer 실행
  → PASS 18 / FAIL 2 (MAJOR 1, MINOR 1)

[Phase 3] FIX 루프 — Round 1/2
  → application-builder: FIX-REQUEST 2개 수정
  → 컴파일: ✅
  → application-reviewer 재리뷰: PASS 20 / FAIL 0 ✅

[Phase 4] application-test-designer 실행
  → 테스트 8개 작성
  → 실행: 8/8 통과 ✅

[Phase 5] 결과 문서화
  → docs/review/STORY-103-app-review.md
  → docs/review/STORY-103-app-test-scenarios.md
  → docs/review/STORY-103-app-harness-result.md

"STORY-103 Application 빌드 파이프라인 완료. 전체 통과."
```

### ESCALATION 발생 시
```
[Phase 3] FIX 루프 — Round 2/2 (최대 도달)
  → 여전히 FAIL 1개

스킬: "FIX 루프 2회를 소진했습니다. 미해결 이슈:"
      1. Service에서 다른 BC의 CommandManager를 직접 호출 — BC 경계 위반

      "어떻게 하시겠습니까?"
      A) PersistenceFacade로 감싸서 처리
      B) 해당 BC의 Service(UseCase)를 호출하도록 변경
      C) 직접 방향을 지정

사용자: A로 가자

스킬: → application-builder에 결정 전달 → 수정 → 재검증
```

---

## 에이전트 호출 프롬프트 템플릿

이 스킬은 `application-harness-orchestrator` 에이전트를 호출할 때 아래 정보를 전달한다:

```
모드: {build | review | test}
대상: {Story 번호 또는 all}
컨벤션: docs/design/convention-02-application.md
구현 가이드: docs/design/phase2-implementation-guide.md (또는 해당 Phase 가이드)
백로그: docs/backlog.md
ERD: docs/erDiagram.md
도메인 코드: domain/src/main/java/com/ryuqq/otatoy/domain/
Application 코드: application/src/main/java/com/ryuqq/otatoy/application/
```
