---
name: integration-harness-orchestrator
description: E2E 통합 테스트 하네스 실행 엔진. test-integration 스킬에서만 호출된다. 설계자 → 생성자 → 검증자 순서 제어, FIX 루프, 에스컬레이션을 관리한다.
---

# Integration Harness Orchestrator

## 역할
E2E 통합 테스트의 시나리오 설계 → 코드 생성 → 검증 → 수정 파이프라인을 **강제 실행**하는 엔진.
각 에이전트를 정해진 순서로 호출하고, FIX 루프를 추적하며, 결과를 수집한다.

**핵심 원칙**: 생성자가 만든 테스트 코드는 반드시 검증자를 거친다. 검증자가 PASS한 코드만 실행한다.

---

## 실행 흐름

### 모드: run (전체 파이프라인)

```
Phase 0: 전제조건 확인
  → Controller 코드 존재 확인
  → MySqlTestContainerConfig 존재 확인
  → bootstrap 모듈 build.gradle.kts에 테스트 의존성 확인
  → 과제 필수 요구사항 6개 확인

Phase 1a: seed-data-designer 호출
  → Flyway DDL + 도메인 Enum 분석
  → 시나리오에 필요한 시드 데이터 SQL 생성
  → db/seed/V999_*.sql 생성
  → infra/local-dev/seed.sh 생성
  → Docker 환경에서 수동 테스트 가능 상태 확보

Phase 1b: test-scenario-designer 호출
  → 대상 모듈의 Controller 엔드포인트 분석
  → P0/P1/P2 시나리오 설계
  → docs/test-scenarios/{대상}-scenarios.md 생성
  → 매니페스트 수집 (시나리오 수, 유형별 분류)

Phase 2: e2e-test-generator 호출
  → 시나리오 문서 기반 테스트 코드 생성
  → E2ETestBase 생성 (없는 경우)
  → 테스트 클래스 생성
  → 컴파일 확인 (./gradlew :bootstrap:{module}:compileTestJava)
  → 매니페스트 수집 (생성 파일 목록, 테스트 수)

Phase 3: e2e-test-reviewer 호출
  → 8개 체크리스트 검증
  → 보고서 수집

Phase 4: FIX 루프 (최대 2회)
  → FAIL 항목이 있으면:
    → FIX-REQUEST를 e2e-test-generator에게 전달
    → generator 수정 → 컴파일 확인
    → e2e-test-reviewer 재검증 (FAIL 항목만)
    → 여전히 FAIL이면 루프 반복
  → 2회 초과 시 → ESCALATION (사용자에게 보고)

Phase 5: 테스트 실행
  → ./gradlew :bootstrap:{module}:test --tests "*E2ETest*"
  → 실패하는 테스트가 있으면:
    → FIX-REQUEST를 e2e-test-generator에게 전달
    → generator 수정 → 테스트 재실행
    → 최대 2회 루프

Phase 6: 결과 문서화 + 완료 보고
  → 시나리오 문서: docs/test-scenarios/
  → 테스트 결과: 통과/실패 요약
  → 전체 요약: 사용자에게 보고
```

### 모드: scenario (시나리오만)

```
Phase 0: 전제조건 확인
Phase 1: test-scenario-designer 호출
Phase 6: 결과 문서화
```

### 모드: verify (검증만)

```
Phase 0: 전제조건 확인
Phase 3: e2e-test-reviewer 호출
Phase 4: FIX 루프 (FAIL 시)
Phase 5: 테스트 실행
Phase 6: 결과 문서화
```

---

## 에이전트 호출 순서

```
[integration-harness-orchestrator]
    │
    ├── Phase 1a: seed-data-designer (시드 데이터)
    │     └── DB 시드 SQL + seed.sh 생성
    │
    ├── Phase 1b: test-scenario-designer (설계자)
    │     └── 시나리오 문서 생성
    │
    ├── Phase 2: e2e-test-generator (생성자)
    │     └── 테스트 코드 생성
    │
    ├── Phase 3: e2e-test-reviewer (검증자)
    │     └── 체크리스트 검증 + FIX-REQUEST
    │
    ├── Phase 4: FIX 루프
    │     ├── e2e-test-generator (수정)
    │     └── e2e-test-reviewer (재검증)
    │
    └── Phase 5: 테스트 실행 (Bash)
```

---

## FIX 루프 규칙

1. BLOCKER FAIL → 반드시 FIX (루프 진입)
2. MAJOR FAIL → FIX 권장 (루프 진입)
3. MINOR FAIL → FIX 선택 (루프 미진입, 경고만)
4. 최대 2회 루프 후에도 BLOCKER FAIL → ESCALATION

### ESCALATION 포맷
```
## ESCALATION: E2E 테스트 FIX 루프 초과

### 해결되지 않은 항목
- E2E-R-003: 데이터 격리 — reservationSession 삭제 순서 문제

### 시도한 수정
1. 1차: deleteAll() 순서 변경 → FK 제약 에러
2. 2차: @Sql 기반 TRUNCATE → Flyway 충돌

### 선택지
A. 해당 테스트 @Disabled 처리하고 진행
B. 수동으로 직접 수정
C. 데이터 격리 방식을 변경 (TRUNCATE → deleteAll 순서 조정)
```

---

## 대상별 bootstrap 모듈 매핑

| 대상 | bootstrap 모듈 | 테스트 위치 |
|------|---------------|------------|
| extranet | bootstrap-extranet | bootstrap/bootstrap-extranet/src/test/ |
| customer | bootstrap-customer | bootstrap/bootstrap-customer/src/test/ |
| concurrency | bootstrap-customer | bootstrap/bootstrap-customer/src/test/ |
| reservation-flow | bootstrap-customer | bootstrap/bootstrap-customer/src/test/ |
| all | 양쪽 모두 | 양쪽 모두 |

---

## 전제조건 체크 목록

| 항목 | 확인 방법 |
|------|----------|
| Controller 존재 | Glob: adapter-in/rest-api-{module}/src/main/java/**/controller/*.java |
| Testcontainers 설정 | File: adapter-out/persistence-mysql/src/test/**/MySqlTestContainerConfig.java |
| bootstrap 빌드 | Bash: ./gradlew :bootstrap:bootstrap-{module}:compileJava |
| 테스트 의존성 | Read: bootstrap/bootstrap-{module}/build.gradle.kts → testcontainers 존재 |
