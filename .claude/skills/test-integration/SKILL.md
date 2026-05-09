---
name: test-integration
description: |
  Testcontainers 기반 E2E 통합 테스트 하네스. 시나리오 설계 → 코드 생성 → 검증 → 수정 파이프라인을 강제 실행한다.
  "통합 테스트", "integration test", "E2E 테스트", "시나리오 테스트",
  "전체 흐름 테스트", "예약 흐름 테스트", "동시성 테스트" 등의 요청에 사용한다.
  통합 테스트를 만들거나 기존 통합 테스트를 검증할 때 반드시 이 하네스를 통해 실행한다.
---

# 통합 테스트 하네스

## 개요

E2E 통합 테스트의 품질을 **파이프라인으로 강제**하는 실행 하네스.
"시나리오를 설계한다 → 코드를 생성한다 → 검증자가 확인한다 → 수정한다"를 빠짐없이 수행한다.

**문제**: 한 에이전트가 시나리오 설계부터 코드 생성까지 하면 자기 검증이 안 되어 빠뜨리는 케이스가 발생한다.
**해결**: 설계자(test-scenario-designer) → 생성자(e2e-test-generator) → 검증자(e2e-test-reviewer)로 분리하여 교차 검증한다.

---

## 실행 모드

### 모드 1: 전체 (`/test-integration run {대상}`)
시나리오 설계 → 코드 생성 → 검증 → 수정 전체 파이프라인을 돌린다.

```
/test-integration run extranet
/test-integration run customer
/test-integration run concurrency
/test-integration run all
```

### 모드 2: 시나리오만 (`/test-integration scenario {대상}`)
시나리오 문서만 생성한다 (코드 미생성).

```
/test-integration scenario all
```

### 모드 3: 검증만 (`/test-integration verify {대상}`)
기존 E2E 테스트 코드를 검증자가 확인한다.

```
/test-integration verify extranet
```

---

## 대상 단위

| 대상 | 범위 |
|------|------|
| `extranet` | Extranet API 9개 엔드포인트 |
| `customer` | Customer API 5개 엔드포인트 |
| `concurrency` | 동시성 시나리오 (재고 동시 차감) |
| `reservation-flow` | 예약 전체 흐름 (세션→확정→취소) |
| `all` | 전체 |

---

## 실행 시 이 스킬이 하는 것

1. 사용자 커맨드를 파싱한다 (모드 + 대상)
2. `integration-harness-orchestrator` 에이전트를 호출한다
3. 에이전트가 반환하는 중간 결과를 사용자에게 보고한다
4. ESCALATION이 발생하면 사용자에게 선택지를 제시한다
5. 최종 결과를 요약하여 보고한다

---

## 사용자 인터랙션

### 정상 흐름
```
사용자: /test-integration run all

스킬: "전체 E2E 통합 테스트 하네스를 시작합니다."
      → integration-harness-orchestrator 호출

[Phase 0] 전제조건 확인
  → Controller 코드 존재 ✅
  → Testcontainers 설정 존재 ✅
  → bootstrap 모듈 빌드 가능 ✅

[Phase 1] test-scenario-designer 실행
  → 엔드포인트 분석: Extranet 9개, Customer 5개
  → P0 시나리오 8개, P1 시나리오 6개, P2 시나리오 4개
  → docs/test-scenarios/ 에 문서 생성

[Phase 2] e2e-test-generator 실행
  → E2ETestBase 생성
  → ExtranetPropertyE2ETest 생성 (P0: 3개, P1: 2개)
  → CustomerReservationE2ETest 생성 (P0: 2개, P1: 3개)
  → CustomerConcurrencyE2ETest 생성 (P0: 1개)
  → 컴파일 확인 ✅

[Phase 3] e2e-test-reviewer 검증
  → 체크리스트 검증 (8개 항목)
  → FAIL: "예약 취소 후 재고 복구 검증 누락"
  → FIX-REQUEST → e2e-test-generator에 전달

[Phase 4] FIX 루프 (최대 2회)
  → generator 수정 → reviewer 재검증
  → PASS ✅

[Phase 5] 테스트 실행
  → ./gradlew test --tests "*E2ETest*"
  → 18/18 통과 ✅

[Phase 6] 결과 문서화

"전체 E2E 통합 테스트 하네스 완료."
```

---

## 에이전트 호출 프롬프트 템플릿

이 스킬은 `integration-harness-orchestrator` 에이전트를 호출할 때 아래 정보를 전달한다:

```
모드: {run | scenario | verify}
대상: {extranet | customer | concurrency | reservation-flow | all}
Controller 위치:
  - Extranet: adapter-in/rest-api-extranet/src/main/java/.../controller/
  - Customer: adapter-in/rest-api-customer/src/main/java/.../controller/
Testcontainers 설정: adapter-out/persistence-mysql/src/test/.../MySqlTestContainerConfig.java
Bootstrap 모듈: bootstrap/bootstrap-extranet, bootstrap/bootstrap-customer
시나리오 문서: docs/test-scenarios/
과제 필수 요구사항:
  1. 파트너 숙소 등록/관리
  2. 고객 숙소 검색/요금 조회
  3. 대규모 요금 조회 동시 처리
  4. 예약/취소
  5. 동시 재고 동시성 제어
  6. Supplier 통합
```
