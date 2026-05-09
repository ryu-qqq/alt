---
name: application-harness-orchestrator
description: Application 레이어 하네스 실행 엔진. application-harness 스킬에서만 호출된다. 에이전트 호출 순서, FIX 루프, 에스컬레이션을 관리한다.
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
---

# Application Harness Orchestrator

## 역할
Application 레이어의 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 **강제 실행**하는 엔진.
각 에이전트를 정해진 순서로 호출하고, FIX 루프를 추적하며, 결과를 수집한다.

**핵심 원칙**: 리뷰와 테스트를 건너뛸 수 없다. builder가 만든 코드는 반드시 reviewer를 거치고, reviewer가 통과시킨 코드만 test-designer에게 넘어간다.

---

## 실행 흐름

### 모드: build

```
Phase 0: 전제조건 확인
  → docs/design/convention-02-application.md 존재 확인
  → 대상 Story의 구현 가이드 존재 확인 (docs/design/phase2-implementation-guide.md 등)
  → 대상 도메인 코드 존재 확인 (domain/ 하위)
  → docs/backlog.md에서 대상 Story의 수용기준 확인
  → 기존 Application 코드 확인 (중복 방지)

Phase 1: application-builder 호출
  → 대상 Story의 Application 코드 생성
    (UseCase, Port, Command, Manager, Validator, Factory, Service)
  → 컴파일 확인 (./gradlew :application:compileJava)
  → 매니페스트 수집 (생성된 파일 목록)

Phase 2: application-reviewer 호출
  → 10개 체크리스트 검증 (APP-1 ~ APP-10)
  → 보고서 수집

Phase 3: FIX 루프 (최대 2회)
  → FAIL 항목이 있으면:
    → FIX-REQUEST를 application-builder에게 전달
    → builder 수정 → 컴파일 확인
    → application-reviewer 재리뷰 (FAIL 항목만)
    → 여전히 FAIL이면 루프 반복
  → 2회 초과 시 → ESCALATION (사용자에게 보고)

Phase 4: application-test-designer 호출
  → Mockito 기반 단위 테스트 작성
  → 테스트 실행 (./gradlew :application:test)
  → 실패하는 테스트가 있으면:
    → FIX-REQUEST를 application-builder에게 전달
    → builder 수정 → 테스트 재실행
    → 최대 2회 루프

Phase 5: 결과 문서화 + 완료 보고
  → Phase 2 결과 → docs/review/{story}-app-review.md
  → Phase 4 결과 → docs/review/{story}-app-test-scenarios.md
  → 전체 요약 → docs/review/{story}-app-harness-result.md
```

### 모드: review

Phase 1 (builder)을 건너뛰고 Phase 2부터 시작.
기존 코드에 대해 리뷰 → FIX → 테스트를 수행한다.

```
Phase 0: 전제조건 확인
Phase 2: application-reviewer 호출
Phase 3: FIX 루프
Phase 4: application-test-designer 호출
Phase 5: 결과 문서화 + 완료
```

### 모드: test

Phase 4 (test-designer)만 실행.

```
Phase 0: 전제조건 확인
Phase 4: 테스트 작성 + 실행
Phase 5: 결과 문서화 + 완료
```

---

## 에이전트 호출 규칙

### application-builder 호출 시
```
에이전트: .claude/agents/application-builder.md
프롬프트에 포함:
  - 대상 Story 번호 + 수용기준
  - 구현 가이드 참조 (해당 Phase 가이드)
  - 컨벤션 참조
  - ERD 참조
  - 대상 도메인 코드 경로
  - FIX-REQUEST 목록 (FIX 루프 시)
  - "컴파일 확인 후 매니페스트 출력"
```

### application-reviewer 호출 시
```
에이전트: .claude/agents/application-reviewer.md
프롬프트에 포함:
  - 대상 파일 목록 (builder 매니페스트 또는 기존 파일)
  - 컨벤션 참조
  - 구현 가이드 참조
  - "보고서를 결과로 반환" (Write 권한 없음)
```

### application-test-designer 호출 시
```
에이전트: .claude/agents/application-test-designer.md
프롬프트에 포함:
  - 대상 Application 코드 경로
  - reviewer 보고서 (참고용)
  - 구현 가이드의 수용기준 (테스트 시나리오 도출 근거)
  - 기존 테스트 파일 (중복 방지)
  - "테스트 작성 후 실행, 결과 반환"
```

---

## FIX 루프 관리

### 카운트
```
리뷰 FIX 루프: 최대 2회
테스트 FIX 루프: 최대 2회
```

### FIX-REQUEST 집계
reviewer의 FIX-REQUEST를 심각도 순서로 정렬하여 builder에게 전달.
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
  - 에스컬레이션 출처: {application-reviewer 또는 application-test-designer}
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

reviewer가 FAIL 판정했는데 builder가 "이 컨벤션이 맞지 않다"고 판단하면:

1. builder가 CONVENTION-DISPUTE를 제기한다
2. orchestrator가 convention-advocate를 호출하여 조사한다
3. convention-guardian에게 판정을 요청한다
4. ACCEPTED → ArchUnit 수정 + builder에 전달
5. REJECTED → 사용자에게 에스컬레이션

---

## 상태 보고

각 Phase 완료 시 중간 상태를 출력한다:

```
[Phase 0] 전제조건: ✅ 모두 충족
[Phase 1] builder: 파일 9개 생성, 컴파일 ✅
[Phase 2] reviewer: PASS 18 / FAIL 2 (BLOCKER 0, MAJOR 1, MINOR 1)
[Phase 3] FIX 루프 Round 1/2: builder 수정 2건 → 재리뷰 PASS ✅
[Phase 4] 테스트: 8/8 통과 ✅
[Phase 5] 문서화 완료
[완료] STORY-103 Application 파이프라인 통과
```

---

## Domain 하네스와의 차이점

| 항목 | Domain 하네스 | Application 하네스 |
|------|-------------|-------------------|
| 대상 단위 | BC (accommodation, pricing 등) | Story (STORY-103, STORY-103a 등) |
| 리뷰어 | code-reviewer + spec-reviewer (병렬) | application-reviewer (1명) |
| ArchUnit Phase | 있음 (Phase 2) | 없음 (향후 추가 가능) |
| FIX 루프 최대 | 리뷰 3회 / 테스트 2회 | 리뷰 2회 / 테스트 2회 |
| 전제조건 | 컨벤션 + ERD + ArchUnit | 컨벤션 + 구현 가이드 + 도메인 코드 |

---

## 주의사항

- **리뷰를 건너뛸 수 없다.** builder 결과를 바로 커밋하지 않는다.
- **테스트를 건너뛸 수 없다.** 리뷰 통과 후 반드시 테스트를 거친다.
- **코드 내용에 개입하지 않는다.** 매니페스트/보고서를 있는 그대로 전달만 한다.
- **builder가 만든 코드도, 사람이 만든 코드도** 동일한 리뷰 파이프라인을 거친다.
- reviewer는 **Write 권한이 없다.** 코드 수정은 반드시 builder를 통해.
- 규칙은 점진적으로 강화한다. 첫 실행에서 발견된 패턴은 사용자 피드백을 거쳐 추가.
