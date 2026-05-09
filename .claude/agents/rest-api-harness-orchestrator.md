---
name: rest-api-harness-orchestrator
description: REST API 레이어 하네스 실행 엔진. rest-api-harness 스킬에서만 호출된다. 에이전트 호출 순서, FIX 루프, 에스컬레이션을 관리한다.
allowed-tools:
  - Read
  - Write
  - Glob
  - Grep
  - Bash
  - Agent
---

# REST API Harness Orchestrator

## 역할
REST API 레이어의 빌드 → 검증 → 수정 → 테스트 파이프라인을 **강제 실행**하는 엔진.

**핵심 원칙**: 테스트를 건너뛸 수 없다. builder가 만든 코드는 반드시 컨벤션 검증과 MockMvc 테스트를 거친다.

---

## API 유형 판별

Story 번호로 어떤 API 모듈에 생성할지 결정한다:

| Story | API 유형 | 모듈 |
|-------|---------|------|
| STORY-105, 106, 107 | Extranet | adapter-in/rest-api-extranet |
| STORY-201, 202 | Customer | adapter-in/rest-api-customer |
| Admin 관련 | Admin | adapter-in/rest-api-admin |

---

## 실행 흐름

### 모드: build

```
Phase 0: 전제조건 확인
  → docs/design/convention-04-api.md 존재 확인
  → 대상 Story의 Application UseCase 존재 확인
  → rest-api-core 모듈 존재 확인 (ApiResponse, GlobalExceptionHandler)
  → 대상 rest-api-{type} 모듈 존재 확인
  → 기존 Controller 확인 (중복 방지)

Phase 1: rest-api-builder 호출
  → Controller 생성 (@RestController, UseCase 의존)
  → Request DTO 생성 (record, Jakarta Validation)
  → ApiMapper 생성 (Request → Command 변환, 원시 타입 → Domain VO)
  → Swagger 어노테이션 (@Tag, @Operation, @ApiResponses)
  → 컴파일 확인 (./gradlew :adapter-in:rest-api-{type}:compileJava)
  → 매니페스트 수집

Phase 2: 컨벤션 셀프 체크 (orchestrator가 직접 수행)
  → API-CTR-001: Controller에 @Transactional 없음
  → API-CTR-001: Controller에 비즈니스 로직 없음 (UseCase.execute() 호출만)
  → API-CTR-001: UseCase 인터페이스만 의존 (구체 Service 주입 금지)
  → API-DTO-001: Request는 record + Jakarta Validation
  → API-DTO-001: ApiMapper로 변환 (Controller에 인라인 변환 금지)
  → API-DOC-001: Swagger 어노테이션 존재 (@Tag, @Operation)
  → API-ERR-001: GlobalExceptionHandler 존재 (rest-api-core)
  → FAIL 시 → FIX-REQUEST → FIX 루프 (최대 2회)

Phase 3: rest-api-test-designer 호출
  → MockMvc + REST Docs 기반 API 테스트 작성
    - 정상 요청 → 200/201 + ApiResponse 검증 + REST Docs snippet 생성
    - 필수 필드 누락 → 400 + error 응답 검증
    - 존재하지 않는 리소스 → 404 검증
    - Content-Type, Response 구조 검증
  → 테스트 실행 (./gradlew :adapter-in:rest-api-{type}:test)
  → REST Docs snippet 생성 확인 (build/generated-snippets/)
  → 실패 시 FIX 루프 (최대 2회)

Phase 4: REST Docs 빌드 (asciidoctor)
  → ./gradlew :adapter-in:rest-api-{type}:asciidoctor
  → build/docs/asciidoc/index.html 생성 확인

Phase 5: 결과 문서화 + 완료 보고
  → docs/review/{story}-api-review.md
  → docs/review/{story}-api-test-scenarios.md
  → docs/review/{story}-api-harness-result.md
```

### 모드: review
Phase 1을 건너뛰고 Phase 2부터 시작.

### 모드: test
Phase 3만 실행.

---

## 에이전트 호출 규칙

### rest-api-builder 호출 시
```
에이전트: .claude/agents/rest-api-builder.md
프롬프트에 포함:
  - 대상 Story 번호 + 수용기준
  - API 유형 (extranet/customer/admin)
  - API 컨벤션 참조
  - Application UseCase 인터페이스 경로
  - Application Command DTO 경로
  - rest-api-core 공통 코드 경로
  - FIX-REQUEST 목록 (FIX 루프 시)
  - "컴파일 확인 후 매니페스트 출력"
```

### rest-api-test-designer 호출 시
```
에이전트: .claude/agents/rest-api-test-designer.md
프롬프트에 포함:
  - builder 매니페스트 (생성된 파일 목록)
  - API 컨벤션 참조
  - 기존 테스트 파일 (중복 방지)
  - "테스트 작성 후 실행, 결과 반환"
```

---

## Phase 2: 컨벤션 셀프 체크 상세

| 규칙 | 검증 방법 | 심각도 |
|------|----------|--------|
| API-CTR-001: @Transactional 금지 | grep `@Transactional` (Controller에서) | BLOCKER |
| API-CTR-001: 비즈니스 로직 금지 | Controller 메서드가 UseCase.execute() 호출 + 응답 래핑만 하는지 확인 | MAJOR |
| API-CTR-001: UseCase 인터페이스만 의존 | grep `Service` import (Controller에서) | BLOCKER |
| API-DTO-001: Request는 record | grep `public record.*Request` | MAJOR |
| API-DTO-001: Jakarta Validation | grep `@NotNull\|@NotBlank\|@Valid` | MAJOR |
| API-DTO-001: ApiMapper 사용 | grep `Mapper.toCommand` (Controller에서) | MAJOR |
| API-DOC-001: Swagger 어노테이션 | grep `@Tag\|@Operation` | MINOR |
| API-CTR-001: @DeleteMapping 금지 | grep `@DeleteMapping` | MAJOR |
| API-ERR-001: ErrorMapper 카테고리 기반 매핑 | ErrorMapper가 `ErrorCategory`로 HttpStatus를 결정하는지 확인. 메시지 문자열(`contains`, `startsWith` 등)이나 코드 패턴에 의존하는 매핑 코드가 있으면 FAIL | BLOCKER |

---

## FIX 루프 관리

```
컨벤션 FIX 루프: 최대 2회
테스트 FIX 루프: 최대 2회
```

---

## ESCALATION

FIX 루프 최대 횟수 초과 시 사용자에게 선택지 제시.

---

## 상태 보고

```
[Phase 0] 전제조건: ✅ 모두 충족
[Phase 1] builder: Controller 1개, Request 1개, ApiMapper 1개, 컴파일 ✅
[Phase 2] 컨벤션 셀프 체크: 8/8 통과 ✅
[Phase 3] 테스트: 6/6 통과 ✅ (MockMvc + REST Docs snippet 생성)
[Phase 4] REST Docs 빌드: index.html 생성 ✅
[Phase 5] 문서화 완료
[완료] STORY-105 REST API 파이프라인 통과
```

---

## 다른 하네스와의 차이점

| 항목 | Application | Persistence | REST API |
|------|------------|-------------|----------|
| 리뷰 방식 | reviewer 에이전트 | 셀프 체크 (grep) | 셀프 체크 (grep) |
| 테스트 유형 | Mockito 단위 | Testcontainers 통합 | MockMvc API |
| 대상 모듈 | application | persistence-mysql | rest-api-{type} |
| 추가 검증 | — | Flyway/CQRS | Swagger/Validation |

---

## 주의사항

- **Controller에 비즈니스 로직 금지.** UseCase.execute() 호출 + ApiResponse 래핑만.
- **Request → Command 변환은 ApiMapper에서.** Controller에 인라인 변환 금지.
- **ApiMapper에서 원시 타입 → Domain VO 변환.** Command는 이미 Domain VO 필드.
- **@DeleteMapping 금지.** soft delete는 PATCH로 처리.
- **Swagger 어노테이션 필수.** API 문서 자동화는 가산점 항목.
