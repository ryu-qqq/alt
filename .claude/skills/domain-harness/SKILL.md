---
name: domain-harness
description: |
  도메인 레이어 전용 하네스. 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 강제 실행한다.
  "도메인 하네스", "도메인 파이프라인", "domain harness", "도메인 빌드",
  "도메인 리뷰 돌려줘", "도메인 검증", "BC 빌드", "도메인 수정 검증" 등의 요청에 사용한다.
  도메인 코드를 만들거나, 기존 도메인 코드를 검증할 때 반드시 이 하네스를 통해 실행한다.
---

# 도메인 하네스

## 개요

도메인 레이어의 코드 품질을 **파이프라인으로 강제**하는 실행 하네스.
"만든다 → 리뷰한다 → 고친다 → 테스트한다"를 빠짐없이 수행하고, 각 단계의 결과를 다음 단계에 전달한다.

**문제**: builder에게 코드 생성을 시키고 리뷰/테스트 없이 바로 커밋하면 품질이 보장되지 않는다.
**해결**: 이 하네스가 전체 흐름을 오케스트레이션하여 리뷰와 테스트를 건너뛸 수 없게 한다.

---

## 실행 모드

### 모드 1: 빌드 (`/domain-harness build {대상}`)
신규 도메인 코드를 생성하고 전체 파이프라인을 돌린다.

예시:
```
/domain-harness build inventory
/domain-harness build reservation
/domain-harness build accommodation
```

### 모드 2: 리뷰 (`/domain-harness review {대상}`)
기존 도메인 코드를 리뷰하고, FIX가 필요하면 수정 루프를 돌린다.

예시:
```
/domain-harness review accommodation
/domain-harness review all
```

### 모드 3: 테스트 (`/domain-harness test {대상}`)
도메인 테스트만 작성/실행한다 (코드는 이미 있다고 가정).

예시:
```
/domain-harness test pricing
/domain-harness test all
```

---

## 실행 시 이 스킬이 하는 것

1. 사용자 커맨드를 파싱한다 (모드 + 대상 BC)
2. `domain-harness-orchestrator` 에이전트를 호출한다
3. 에이전트가 반환하는 중간 결과를 사용자에게 보고한다
4. ESCALATION이 발생하면 사용자에게 선택지를 제시한다
5. 최종 결과를 요약하여 보고한다

---

## 사용자 인터랙션

### 정상 흐름
```
사용자: /domain-harness review accommodation

스킬: "accommodation 도메인 리뷰 파이프라인을 시작합니다."
      → domain-harness-orchestrator 호출

[Phase 1] ArchUnit 테스트 실행
  → ✅ 12/12 통과

[Phase 2] code-reviewer + spec-reviewer 병렬 실행
  → code-reviewer: PASS 20 / FAIL 3 (MAJOR 2, MINOR 1)
  → spec-reviewer: ✅ 12 / ⚠️ 2 / ❌ 1

[Phase 3] FIX 루프 — Round 1/3
  → domain-builder: FIX-REQUEST 5개 수정
  → 컴파일: ✅
  → code-reviewer 재리뷰: PASS 23 / FAIL 0 ✅
  → spec-reviewer 재리뷰: ✅ 14 / ⚠️ 1 ✅ (허용 범위)

[Phase 4] domain-test-designer 실행
  → 테스트 15개 작성
  → 실행: 14 성공 / 1 실패

[Phase 5] FIX 루프 — Round 2/3
  → domain-builder: 실패 테스트 기반 수정
  → 테스트 재실행: 15/15 통과 ✅

"accommodation 도메인 리뷰 파이프라인 완료. 전체 통과."
```

### ESCALATION 발생 시
```
[Phase 3] FIX 루프 — Round 3/3 (최대 도달)
  → 여전히 FAIL 2개

스킬: "FIX 루프 3회를 소진했습니다. 미해결 이슈:"
      1. RateRule 기간 중복 검증 — 도메인에서 할지 Application에서 할지
      2. Property 비활성화 시 하위 RoomType 연쇄 — 도메인 메서드 vs Application 서비스

      "어떻게 하시겠습니까?"
      A) Application에서 처리하도록 도메인에서는 제외
      B) 도메인 서비스를 만들어서 처리
      C) 직접 방향을 지정

사용자: A로 가자

스킬: → domain-builder에 결정 전달 → 수정 → 재검증
```

---

## 에이전트 호출 프롬프트 템플릿

이 스킬은 `domain-harness-orchestrator` 에이전트를 호출할 때 아래 정보를 전달한다:

```
모드: {build | review | test}
대상: {BC명 또는 all}
대상 경로: domain/src/main/java/com/ryuqq/otatoy/domain/{BC}/
컨벤션: docs/design/convention-01-domain.md
ERD: docs/erDiagram.md
ArchUnit: domain/src/test/java/com/ryuqq/otatoy/domain/DomainLayerArchTest.java
```
