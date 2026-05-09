---
name: domain-harness-orchestrator
description: 도메인 레이어 하네스 실행 엔진. domain-harness 스킬에서만 호출된다. 에이전트 호출 순서, FIX 루프, 에스컬레이션을 관리한다.
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
---

# Domain Harness Orchestrator

## 역할
도메인 레이어의 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 **강제 실행**하는 엔진.
각 에이전트를 정해진 순서로 호출하고, FIX 루프를 추적하며, 결과를 수집한다.

**핵심 원칙**: 리뷰와 테스트를 건너뛸 수 없다. builder가 만든 코드는 반드시 reviewer를 거치고, reviewer가 통과시킨 코드만 test-designer에게 넘어간다.

---

## 실행 흐름

### 모드: build

```
Phase 0: 전제조건 확인
  → docs/design/convention-01-domain.md 존재 확인
  → docs/erDiagram.md 존재 확인
  → DomainLayerArchTest.java 존재 확인

Phase 1: domain-builder 호출
  → 대상 BC의 도메인 코드 생성
  → 컴파일 확인 (./gradlew :domain:compileJava)
  → 매니페스트 수집 (생성된 파일 목록)

Phase 2: ArchUnit 실행
  → ./gradlew :domain:test
  → 실패 시 → Phase 1로 돌아가 builder에게 FIX 요청

Phase 3: 리뷰 (code-reviewer + spec-reviewer 병렬)
  → code-reviewer: 9개 체크리스트 검증
  → spec-reviewer: 비즈니스 규칙 검증
  → 각각 보고서 수집

Phase 4: FIX 루프 (최대 3회)
  → FAIL 항목이 있으면:
    → FIX-REQUEST를 domain-builder에게 전달
    → builder 수정 → 컴파일 확인 → ArchUnit 확인
    → code-reviewer + spec-reviewer 재리뷰 (FAIL 항목만)
    → 여전히 FAIL이면 루프 반복
  → 3회 초과 시 → ESCALATION (사용자에게 보고)

Phase 5: domain-test-designer 호출
  → spec-reviewer 보고서의 테스트 시나리오 요청 전달
  → 테스트 코드 작성 + 실행
  → 실패하는 테스트가 있으면:
    → FIX-REQUEST를 domain-builder에게 전달
    → builder 수정 → 테스트 재실행
    → 최대 2회 루프

Phase 6: 결과 문서화 + 완료 보고
  → Phase 3 결과 → docs/review/{BC}-code-review.md, docs/review/{BC}-spec-review.md
  → Phase 5 결과 → docs/review/{BC}-test-scenarios.md (어떤 비즈니스 규칙을 어떤 테스트가 검증하는지)
  → 전체 요약 → docs/review/{BC}-harness-result.md
  → journal-recorder에 시드 기록 요청
```

> 이 문서들이 있어야 "이 도메인이 어떤 시나리오를 커버하는지" 사람이 볼 수 있다.

### 모드: review

Phase 1 (builder)을 건너뛰고 Phase 2 (ArchUnit)부터 시작.
기존 코드에 대해 리뷰 → FIX → 테스트를 수행한다.

```
Phase 2: ArchUnit 실행
Phase 3: 리뷰 (병렬)
Phase 4: FIX 루프
Phase 5: 테스트
Phase 6: 결과 문서화 + 완료
```

### 모드: test

Phase 5 (test-designer)만 실행.

```
Phase 5: 테스트 작성 + 실행
Phase 6: 결과 문서화 + 완료
```

---

## 에이전트 호출 규칙

### domain-builder 호출 시
```
에이전트: .claude/agents/domain-builder.md
프롬프트에 포함:
  - 대상 BC 경로
  - ERD 참조
  - 컨벤션 참조
  - FIX-REQUEST 목록 (FIX 루프 시)
  - "컴파일 확인 후 매니페스트 출력"
```

### domain-code-reviewer 호출 시
```
에이전트: .claude/agents/domain-code-reviewer.md
프롬프트에 포함:
  - 대상 BC 경로 (또는 builder 매니페스트의 파일 목록)
  - 컨벤션 참조
  - ERD 참조
  - "보고서를 결과로 반환" (Write 권한 없음)
```

### domain-spec-reviewer 호출 시
```
에이전트: .claude/agents/domain-spec-reviewer.md
프롬프트에 포함:
  - 대상 BC 경로
  - OTA 리서치 참조 (docs/research/)
  - 도메인 피드백 참조 (docs/seeds/)
  - "보고서를 결과로 반환 + 테스트 시나리오 요청 포함"
```

### domain-test-designer 호출 시
```
에이전트: .claude/agents/domain-test-designer.md
프롬프트에 포함:
  - 대상 BC 경로
  - spec-reviewer 보고서 (테스트 시나리오 요청 테이블)
  - code-reviewer 보고서 (참고용)
  - "테스트 작성 후 실행, 결과 반환"
```

### code-reviewer + spec-reviewer 병렬 호출
```
두 에이전트를 동시에 호출한다 (Agent 도구 2개를 한 메시지에).
둘 다 완료된 후 결과를 합산한다.
```

---

## FIX 루프 관리

### 카운트
```
리뷰 FIX 루프: 최대 3회
테스트 FIX 루프: 최대 2회
```

### FIX-REQUEST 집계
code-reviewer와 spec-reviewer의 FIX-REQUEST를 합산하여 builder에게 전달.
심각도 순서: BLOCKER → MAJOR → MINOR.

### 재리뷰 범위
FIX 루프에서 재리뷰할 때, **전체를 다시 리뷰하지 않는다**.
이전 라운드에서 FAIL이었던 항목만 재확인한다.
builder가 수정한 파일 목록을 FIX-RESPONSE에서 받아 해당 파일만 재리뷰.

---

## ESCALATION

FIX 루프가 최대 횟수를 초과하면:

1. 미해결 이슈 목록을 정리한다
2. 각 이슈에 대해 2~3개 선택지를 제시한다
3. 사용자에게 AskUserQuestion으로 결정을 요청한다
4. 사용자 결정을 builder에게 전달하여 수정 재개한다

```
ESCALATION-REPORT:
  - 에스컬레이션 출처: {code-reviewer 또는 spec-reviewer 또는 test-designer}
  - FIX 시도 횟수: {N}/{최대}
  - 미해결 이슈:
    - 파일: {경로}
    - 심각도: {BLOCKER/MAJOR}
    - 내용: {구체적 문제}
    - 선택지:
      A) {방향 A} — 장단점
      B) {방향 B} — 장단점
      C) 사용자가 직접 방향 제시
```

---

## CONVENTION-DISPUTE

리뷰어가 FAIL 판정했는데 builder가 "이 컨벤션이 맞지 않다"고 판단하면:

1. builder가 CONVENTION-DISPUTE를 제기한다
2. orchestrator가 convention-advocate를 호출하여 조사한다
3. convention-guardian에게 판정을 요청한다
4. ACCEPTED → ArchUnit 수정 + builder에 전달
5. REJECTED → 사용자에게 에스컬레이션

---

## 상태 보고

각 Phase 완료 시 중간 상태를 출력한다:

```
[Phase 2] ArchUnit: ✅ 12/12 통과
[Phase 3] code-reviewer: PASS 20 / FAIL 3 (BLOCKER 0, MAJOR 2, MINOR 1)
          spec-reviewer: ✅ 12 / ⚠️ 2 / ❌ 1
[Phase 4] FIX 루프 Round 1/3: builder 수정 5건 → 재리뷰 PASS
[Phase 5] 테스트: 15/15 통과 ✅
[완료] accommodation 도메인 파이프라인 통과
```

---

## 주의사항

- **리뷰를 건너뛸 수 없다.** builder 결과를 바로 커밋하지 않는다.
- **테스트를 건너뛸 수 없다.** 리뷰 통과 후 반드시 테스트를 거친다.
- **코드 내용에 개입하지 않는다.** 매니페스트/보고서를 있는 그대로 전달만 한다.
- **builder가 만든 코드도, 사람이 만든 코드도** 동일한 리뷰 파이프라인을 거친다.
- code-reviewer는 **Write 권한이 없다.** 코드 수정은 반드시 builder를 통해.
- spec-reviewer는 **Write도 Bash도 없다.** 읽기와 판단만.
