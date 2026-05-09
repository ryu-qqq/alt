---
name: pipeline
description: |
  에이전트 파이프라인을 실행하는 오케스트레이터 스킬.
  전체 파이프라인 실행, 특정 레이어 실행, 특정 단계 실행, 산출물 검증, 상태 확인을 지원한다.
  "파이프라인", "pipeline", "전체 실행", "도메인 실행", "빌드 실행",
  "리뷰 실행", "테스트 실행", "감사", "audit", "상태 확인", "status",
  "파이프라인 돌려줘", "STORY 실행" 등의 요청에 사용한다.
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
---

# Pipeline Orchestrator Skill

## 개요

에이전트 파이프라인의 **사용자 진입점**이다.
사용자의 커맨드를 파싱하여 `pipeline-orchestrator` 에이전트를 호출하고,
에이전트 실행 중 사용자 의사결정이 필요한 시점에 중계 역할을 한다.

---

## 지원 커맨드

| 커맨드 | 설명 | 예시 |
|--------|------|------|
| `run <story>` | 전체 파이프라인 실행 (스토리 단위) | `/pipeline run STORY-001` |
| `layer <layer> <story>` | 특정 레이어만 실행 | `/pipeline layer domain STORY-001` |
| `step <step> <layer> <story>` | 특정 단계만 실행 | `/pipeline step build domain STORY-001` |
| `audit [story]` | project-manager 산출물 검증 | `/pipeline audit STORY-001` |
| `status [story]` | 현재 파이프라인 상태 확인 | `/pipeline status` |
| `resume <story>` | ESCALATION/DISPUTE로 중단된 파이프라인 재개 | `/pipeline resume STORY-001` |

### 파라미터 값

**layer:** `domain`, `application`, `adapter-in`, `adapter-out`, `adapter` (adapter-in + adapter-out 병렬)

**step:** `build`, `review`, `test`

---

## 자연어 매핑

사용자가 슬래시 커맨드 대신 자연어로 요청할 수 있다. 아래 패턴을 인식한다:

| 자연어 | 파싱 결과 |
|--------|----------|
| "STORY-001 전체 파이프라인 돌려줘" | command=run, story=STORY-001 |
| "도메인 빌드해줘 STORY-001" | command=step, step=build, layer=domain, story=STORY-001 |
| "Application 리뷰 실행" | command=step, step=review, layer=application (story 물어봄) |
| "지금 상태 어때?" | command=status |
| "산출물 검증해줘" | command=audit (story 물어봄) |
| "이어서 진행해줘 STORY-001" | command=resume, story=STORY-001 |

story 파라미터가 누락되면 사용자에게 물어본다.

---

## 실행 흐름

### 1. 커맨드 파싱

사용자 입력을 아래 구조화된 지시문으로 변환한다:

```markdown
## 파이프라인 실행 요청
- command: {run | layer | step | audit | status | resume}
- story: STORY-{번호}
- layer: {domain | application | adapter-in | adapter-out | adapter | all}
- step: {build | review | test | all}
```

### 2. 시작 전 확인 (run, layer, step 커맨드)

사용자에게 실행 범위를 확인한다:

```
STORY-001 파이프라인을 실행합니다.
- 모드: {전체 실행 / domain 레이어만 / domain build 단계만}
- 백로그: docs/backlog.md 확인됨
- 컨벤션: {존재하는 컨벤션 문서 목록}
계속할까요?
```

### 3. pipeline-orchestrator Agent 호출

파싱된 지시문을 프롬프트로 전달하여 `pipeline-orchestrator` 에이전트를 Agent 도구로 호출한다.

```
Agent 도구 호출:
  subagent_type: (기본)
  description: "파이프라인 실행 STORY-{번호}"
  prompt: |
    ## 파이프라인 실행 요청
    - command: run
    - story: STORY-001
    - layer: all
    - step: all

    아래 에이전트 정의를 참조하여 파이프라인을 실행하세요.
    에이전트 정의: .claude/agents/pipeline-orchestrator.md
```

### 4. 사용자 인터랙션 중계

pipeline-orchestrator가 실행 중 사용자 의사결정이 필요한 시점:

#### ESCALATION (FIX 루프 초과)

오케스트레이터가 ESCALATION 상황과 선택지를 반환하면, Skill이 사용자에게 전달:

```
## ESCALATION 발생 — {팀명}

FIX 루프 {N}/{최대}회 소진. 미해결 이슈가 있습니다.

### 문제 요약
{project-lead 분석 내용}

### 선택지
A) {방향 A} — {설명}
B) {방향 B} — {설명}
C) {방향 C} — {설명} (project-lead 추천)

어떤 방향으로 진행할까요?
```

사용자 응답을 받아 `resume` 커맨드와 함께 오케스트레이터에 전달한다.

#### CONVENTION-DISPUTE REJECTED

```
## 컨벤션 이의 기각 — {규칙 코드}

- 이의 내용: {요약}
- guardian 판정: REJECTED — {기각 사유}

기각을 유지할까요, 아니면 컨벤션을 수정할까요?
1. 기각 유지 (기존 컨벤션대로 수정)
2. 컨벤션 수정 (guardian에게 ArchUnit 변경 지시)
```

### 5. 완료 보고

오케스트레이터 완료 후 결과를 사용자에게 출력:

```
## 파이프라인 완료 — STORY-001

| 레이어 | 상태 | FIX 횟수 | 비고 |
|--------|:----:|:-------:|------|
| Domain | DONE | 1 | spec-reviewer에서 1회 수정 |
| Application | DONE | 0 | |
| Adapter-out | DONE | 0 | |
| Adapter-in | DONE | 1 | API 응답 포맷 수정 |

에스컬레이션: 0건
컨벤션 이의: 0건
산출물 감사: PASS

journal-recorder 시드 {N}건 저장됨
```

### 6. status 커맨드 처리

`docs/pipeline/STORY-{번호}-state.yaml`을 읽어 현재 상태를 보고한다.
state 파일이 없으면 "아직 시작된 파이프라인이 없습니다" 출력.

```
## 파이프라인 상태 — STORY-001

| 레이어 | 상태 | 현재 단계 | FIX |
|--------|:----:|----------|:---:|
| Domain | COMPLETED | - | 1/3 |
| Application | IN_PROGRESS | review | 0/2 |
| Adapter-out | NOT_STARTED | - | 0/2 |
| Adapter-in | NOT_STARTED | - | 0/2 |

전체 상태: IN_PROGRESS
```

---

## 상태 파일 구조

```
docs/pipeline/
├── STORY-{번호}-state.yaml       # 파이프라인 상태
└── STORY-{번호}-manifests/       # 에이전트 매니페스트 수집
    ├── domain-builder.md
    ├── domain-code-review.md
    ├── domain-spec-review.md
    ├── domain-test.md
    ├── application-builder.md
    └── ...
```

---

## 주의사항

- story 파라미터가 누락되면 반드시 사용자에게 물어본다
- status 커맨드만 state.yaml 없이 실행 가능
- resume는 PAUSED 상태인 파이프라인에만 사용 가능
- 파이프라인 실행 중에는 동일 스토리에 대해 다른 run 커맨드를 실행하지 않는다
- ESCALATION/DISPUTE 대기 중에는 state.yaml에 PAUSED 기록
