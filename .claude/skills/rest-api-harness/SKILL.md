---
name: rest-api-harness
description: |
  REST API 레이어 전용 하네스. 빌드 → 리뷰 → 수정 → 테스트 파이프라인을 강제 실행한다.
  "api 하네스", "api 파이프라인", "rest-api harness", "api 빌드",
  "controller 빌드", "controller 검증", "api 리뷰", "api 테스트" 등의 요청에 사용한다.
  REST API 코드를 만들거나, 기존 API 코드를 검증할 때 반드시 이 하네스를 통해 실행한다.
---

# REST API 하네스

## 개요

REST API 레이어의 코드 품질을 **파이프라인으로 강제**하는 실행 하네스.
"만든다 → 검증한다 → 고친다 → 테스트한다"를 빠짐없이 수행한다.

---

## 실행 모드

### 모드 1: 빌드 (`/rest-api-harness build {대상}`)
```
/rest-api-harness build STORY-105
/rest-api-harness build STORY-106
```

### 모드 2: 리뷰 (`/rest-api-harness review {대상}`)
기존 API 코드를 검증한다.

### 모드 3: 테스트 (`/rest-api-harness test {대상}`)
API 테스트만 작성/실행한다.

---

## 대상 단위

Story 단위로 실행한다. Story에 따라 **어떤 API 모듈**에 생성할지 결정한다:
- Extranet API (STORY-105, 106, 107) → `adapter-in/rest-api-extranet/`
- Customer API (STORY-201, 202) → `adapter-in/rest-api-customer/`
- Admin API → `adapter-in/rest-api-admin/`

---

## 실행 시 이 스킬이 하는 것

1. 사용자 커맨드를 파싱한다 (모드 + 대상 Story)
2. `rest-api-harness-orchestrator` 에이전트를 호출한다
3. 에이전트가 반환하는 중간 결과를 사용자에게 보고한다
4. ESCALATION이 발생하면 사용자에게 선택지를 제시한다
5. 최종 결과를 요약하여 보고한다

---

## 사용자 인터랙션

### 정상 흐름
```
사용자: /rest-api-harness build STORY-105

스킬: "STORY-105 REST API 빌드 파이프라인을 시작합니다."
      → rest-api-harness-orchestrator 호출

[Phase 0] 전제조건 확인
  → convention-04-api.md ✅
  → Application UseCase 존재 ✅
  → rest-api-core 모듈 존재 ✅

[Phase 1] rest-api-builder 실행
  → Controller, Request DTO, ApiMapper 생성
  → 컴파일: ✅

[Phase 2] 컨벤션 셀프 체크
  → Controller에 @Transactional 없음 ✅
  → UseCase 인터페이스만 의존 ✅
  → Request는 record + Jakarta Validation ✅
  → Swagger 어노테이션 존재 ✅

[Phase 3] rest-api-test-designer 실행
  → MockMvc 기반 API 테스트 작성
  → 실행: 8/8 통과 ✅

[Phase 4] 결과 문서화

"STORY-105 REST API 빌드 파이프라인 완료."
```

---

## 에이전트 호출 프롬프트 템플릿

```
모드: {build | review | test}
대상: {Story 번호}
API 유형: {extranet | customer | admin}
컨벤션: docs/design/convention-04-api.md
Application UseCase: application/src/main/java/com/ryuqq/otatoy/application/{bc}/port/in/
REST API 코드: adapter-in/rest-api-{type}/src/main/java/
공통 코드: adapter-in/rest-api-core/src/main/java/
```
