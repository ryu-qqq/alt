# API 레이어 컨벤션

## 원칙
- Controller는 **Thin Layer**다. HTTP 요청/응답 변환만 담당하고, 비즈니스 로직은 금지.
- Controller는 UseCase 인터페이스만 의존한다. 구체 Service를 직접 주입하지 않는다.
- 모든 응답은 공통 포맷(`ApiResponse<T>`)으로 래핑한다.
- 예외 처리는 `GlobalExceptionHandler` + `ErrorMapper`에서 일괄 처리한다.
- 에러 응답은 **userMessage**(사용자 노출)와 **debugMessage**(내부 로깅)를 분리한다.

### 왜 이렇게 하는가
헥사고날 아키텍처에서 Controller는 Adapter-In이다. Port-In(UseCase)에만 의존하고, Application 내부 구조를 몰라야 한다. Controller가 Service 구현체에 직접 의존하면 Application 리팩토링이 Controller에 전파된다. Thin Layer로 유지하면 Controller 테스트가 간단해지고(UseCase Mock만 주입), API 변경과 비즈니스 로직 변경이 독립적이 된다.

---

## 규칙 목록

### API-CTR-001: Controller 아키텍처 원칙 [BLOCKER]

`@RestController` 필수. UseCase 인터페이스만 의존한다. `@Transactional` 사용 금지. 비즈니스 로직 금지. **`@DeleteMapping` 사용 금지** — soft delete이므로 `PATCH /{id}/cancel` 또는 `PATCH /{id}/delete`로 처리한다.

```java
@RestController
@RequestMapping("/api/v1/extranet/properties")
@RequiredArgsConstructor
public class ExtranetPropertyController {

    // UseCase 인터페이스만 의존 — 구체 Service 주입 금지
    private final RegisterPropertyUseCase registerPropertyUseCase;
    private final RegisterRoomTypeUseCase registerRoomTypeUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerProperty(
            @Valid @RequestBody RegisterPropertyApiRequest request) {

        // 1. Request → Command 변환 (ApiMapper 사용)
        RegisterPropertyCommand command = PropertyApiMapper.toCommand(request);

        // 2. UseCase 호출
        Long propertyId = registerPropertyUseCase.execute(command);

        // 3. 응답 반환
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(propertyId));
    }
}
```

**금지 사항**:
```java
// 금지 — 비즈니스 로직
@PostMapping
public ResponseEntity<?> register(@RequestBody RegisterPropertyApiRequest request) {
    if (request.name().length() > 200) {  // 비즈니스 검증은 Domain 책임
        throw new IllegalArgumentException("이름 길이 초과");
    }
}

// 금지 — @Transactional
@Transactional  // Controller에서 트랜잭션 관리 금지
@PostMapping
public ResponseEntity<?> register(...) { ... }

// 금지 — 인라인 변환 로직
@GetMapping
public ResponseEntity<?> search(...) {
    // stream().map() 같은 인라인 변환 금지 — ApiMapper 사용
    return properties.stream().map(p -> new PropertyApiResponse(p.getName())).toList();
}
```

**왜**: Controller에 비즈니스 로직이 들어가면 테스트가 HTTP 계층과 비즈니스 로직을 동시에 검증해야 하여 복잡해진다. 트랜잭션은 Application 레이어(Manager)의 책임이다.

---

### API-CTR-002: HTTP 상태 코드 + 응답 래핑 [BLOCKER]

| 메서드 | HTTP 상태 | 비고 |
|--------|-----------|------|
| POST (생성) | 201 CREATED | 생성된 리소스 ID 반환 |
| GET (조회) | 200 OK | |
| PUT/PATCH (수정) | 200 OK | |
| PATCH (취소/삭제) | 200 OK | Soft Delete — 상태 변경 |

모든 응답은 `ApiResponse<T>`로 래핑한다:

```java
// 성공 응답
{
    "success": true,
    "data": { ... }
}

// 에러 응답
{
    "success": false,
    "error": {
        "code": "ACC-001",
        "userMessage": "숙소를 찾을 수 없습니다",
        "debugMessage": "property_id=123 not found in active properties"
    }
}
```

```java
// core 모듈에 정의
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetail error
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String code, String userMessage, String debugMessage) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, userMessage, debugMessage));
    }

    public record ErrorDetail(
        String code,
        String userMessage,    // 사용자에게 노출하는 메시지
        String debugMessage    // 내부 로깅용 상세 메시지
    ) {}
}
```

**왜**: 클라이언트가 모든 API에서 동일한 응답 구조를 기대할 수 있다. `success` 필드로 성공/실패를 즉시 판단하고, 에러 시 `code`로 프로그래밍적 분기가 가능하다. userMessage와 debugMessage를 분리하면 사용자에게는 친절한 메시지를, 개발자에게는 디버깅에 필요한 상세 정보를 제공할 수 있다.

---

### API-CTR-003: 페이지네이션 응답 [MAJOR]

List를 직접 반환하지 않는다. 커서 기반 페이지네이션은 `SliceResponse`로 래핑한다.

```java
public record SliceResponse<T>(
    List<T> content,
    boolean hasNext,
    Long nextCursor
) {
    public static <T> SliceResponse<T> of(List<T> content, boolean hasNext, Long nextCursor) {
        return new SliceResponse<>(content, hasNext, nextCursor);
    }
}
```

**Controller 사용**:
```java
@GetMapping("/api/v1/search/properties")
public ResponseEntity<ApiResponse<SliceResponse<PropertyApiResponse>>> searchProperties(
        @Valid SearchPropertyApiRequest request) {

    PropertySliceResult result = searchPropertyUseCase.execute(
        PropertyApiMapper.toQuery(request));

    SliceResponse<PropertyApiResponse> response = SliceResponse.of(
        result.properties().stream()
            .map(PropertyApiMapper::toApiResponse)
            .toList(),
        result.hasNext(),
        result.nextCursor()
    );

    return ResponseEntity.ok(ApiResponse.success(response));
}
```

**왜**: List를 직접 반환하면 클라이언트가 "다음 페이지가 있는지", "다음 커서는 무엇인지" 알 수 없다. 페이지네이션 메타 정보를 포함하면 클라이언트가 무한 스크롤 등을 쉽게 구현할 수 있다.

---

### API-DTO-001: Request/Response DTO는 Record [BLOCKER]

API 계층의 DTO는 반드시 Java record로 선언한다. DTO 변환은 `{Domain}ApiMapper`에서 처리한다. Request에 `toCommand()` 메서드를 두지 않고, Mapper로 분리한다.

```java
// Request DTO — {Action}{Domain}ApiRequest
public record RegisterPropertyApiRequest(
    @NotNull Long partnerId,
    @NotBlank String name,
    @NotBlank String propertyTypeCode,
    @NotBlank String address,
    double latitude,
    double longitude,
    String neighborhood,
    String region
) {}

// Response DTO — {Domain}ApiResponse
public record PropertyApiResponse(
    Long id,
    String name,
    String propertyType,
    String address,
    String status
) {}

// ApiMapper — 변환 전담
public class PropertyApiMapper {

    // Request → Application Command
    public static RegisterPropertyCommand toCommand(RegisterPropertyApiRequest request) {
        return RegisterPropertyCommand.of(
            request.partnerId(),
            request.name(),
            request.propertyTypeCode(),
            request.address(),
            request.latitude(),
            request.longitude(),
            request.neighborhood(),
            request.region()
        );
    }

    // Application Result → API Response
    public static PropertyApiResponse toApiResponse(PropertyDetailResult result) {
        return new PropertyApiResponse(
            result.id(),
            result.name(),
            result.propertyType(),
            result.address(),
            result.status()
        );
    }

    // Request → Application Query
    public static SearchPropertyQuery toQuery(SearchPropertyApiRequest request) {
        return new SearchPropertyQuery(
            request.region(),
            request.checkIn(),
            request.checkOut(),
            request.guests(),
            request.size(),
            request.cursor()
        );
    }
}
```

**왜**: Record로 불변성을 보장한다. ApiMapper로 변환 로직을 분리하면 Request/Response DTO가 순수 데이터 컨테이너로 유지되고, 변환 로직을 한 곳에서 관리하여 Controller가 Thin Layer를 유지할 수 있다.

---

### API-DTO-002: Jakarta Validation 활용 [MAJOR]

Request DTO에 Jakarta Bean Validation 어노테이션을 사용한다. Controller에서 `@Valid`로 검증을 트리거한다.

```java
public record RegisterPropertyApiRequest(
    @NotNull(message = "파트너 ID는 필수입니다")
    Long partnerId,

    @NotBlank(message = "숙소 이름은 필수입니다")
    @Size(max = 200, message = "숙소 이름은 200자 이하입니다")
    String name,

    @NotBlank(message = "숙소 유형 코드는 필수입니다")
    String propertyTypeCode,

    @NotBlank(message = "주소는 필수입니다")
    String address,

    double latitude,
    double longitude,
    String neighborhood,
    String region
) {}
```

**왜**: 형식 검증(필수 여부, 길이, 범위)은 API 계층에서 처리하고, 비즈니스 검증(유효한 상태 전이, 중복 확인 등)은 Domain/Application에서 처리한다. 검증 실패 시 GlobalExceptionHandler가 400 응답을 자동 생성한다.

---

### API-ERR-001: GlobalExceptionHandler + ErrorMapper (카테고리 기반 매핑) [BLOCKER]

모든 예외는 `@RestControllerAdvice`에서 일괄 처리한다. Controller에서 `try-catch`를 사용하지 않는다. `ErrorMapper`가 DomainException을 userMessage + debugMessage로 변환한다.

**HTTP 상태코드 결정은 `ErrorCategory` 기반으로만 한다.** 에러 메시지 문자열이나 코드 접두사 패턴에 의존하는 매핑은 금지한다. 메시지를 변경하면 HTTP 상태가 바뀌는 것은 심각한 결합이다.

#### ErrorCategory → HttpStatus 매핑 규칙

| ErrorCategory | HttpStatus | 설명 |
|---------------|-----------|------|
| NOT_FOUND | 404 Not Found | 리소스 없음 |
| VALIDATION | 400 Bad Request | 입력값/비즈니스 규칙 위반 |
| CONFLICT | 409 Conflict | 상태 충돌 (재고 부족, 중복 등) |
| FORBIDDEN | 422 Unprocessable Entity | 금지된 행위 (상태 전이 불가 등) |

```java
// ErrorMapper — DomainException → 에러 응답 변환 (카테고리 기반)
@Component
public class ErrorMapper {

    // ErrorCategory → HttpStatus 매핑 (유일한 매핑 규칙)
    private static final Map<ErrorCategory, HttpStatus> CATEGORY_STATUS_MAP = Map.of(
        ErrorCategory.NOT_FOUND,   HttpStatus.NOT_FOUND,       // 404
        ErrorCategory.VALIDATION,  HttpStatus.BAD_REQUEST,     // 400
        ErrorCategory.CONFLICT,    HttpStatus.CONFLICT,        // 409
        ErrorCategory.FORBIDDEN,   HttpStatus.UNPROCESSABLE_ENTITY  // 422
    );

    // ErrorCategory로 HttpStatus 결정 — 메시지/코드 패턴 의존 금지
    public HttpStatus resolveHttpStatus(ErrorCode errorCode) {
        return CATEGORY_STATUS_MAP.getOrDefault(
            errorCode.getCategory(),
            HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // userMessage: 사용자에게 노출하는 메시지
    // debugMessage: args Map에서 변환 — 내부 로깅용 상세 메시지
    public ApiResponse.ErrorDetail toErrorDetail(DomainException e) {
        ErrorCode errorCode = e.getErrorCode();
        String debugMessage = e.getArgs().isEmpty()
            ? errorCode.getMessage()
            : e.getArgs().toString();  // 또는 JSON 직렬화

        return new ApiResponse.ErrorDetail(
            errorCode.getCode(),
            errorCode.getMessage(),     // userMessage — "예약할 수 없습니다"
            debugMessage                // debugMessage — "{inventory_id=123, available=0, requested=1}"
        );
    }
}

// GlobalExceptionHandler
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorMapper errorMapper;

    // DomainException — ErrorMapper로 변환 (카테고리 기반 HttpStatus 결정)
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException e) {
        ApiResponse.ErrorDetail detail = errorMapper.toErrorDetail(e);
        HttpStatus status = errorMapper.resolveHttpStatus(e.getErrorCode());
        log.warn("도메인 예외 발생: {} - {} (HTTP {})", detail.code(), detail.debugMessage(), status.value());

        return ResponseEntity
            .status(status)
            .body(new ApiResponse<>(false, null, detail));
    }

    // Validation 실패 — 필드별 에러 메시지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {

        String userMessage = "입력값이 올바르지 않습니다";
        String debugMessage = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("검증 실패: {}", debugMessage);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_ERROR", userMessage, debugMessage));
    }

    // 예상치 못한 예외 — 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e) {
        log.error("예상치 못한 예외 발생", e);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                "INTERNAL_ERROR",
                "서버 내부 오류가 발생했습니다",     // userMessage
                e.getMessage()                        // debugMessage — 운영에서는 마스킹 고려
            ));
    }
}
```

**금지 사항 -- 메시지/접두사 기반 매핑**:
```java
// 금지 — 메시지 문자열에 의존하는 매핑
if (errorCode.getMessage().contains("찾을 수 없습니다")) {
    return HttpStatus.NOT_FOUND;  // 메시지 변경하면 매핑이 깨진다
}

// 금지 — 코드 접두사에 의존하는 매핑
if (errorCode.getCode().startsWith("ACC-")) {
    return HttpStatus.NOT_FOUND;  // 같은 BC에도 다양한 에러 유형이 있다
}

// 올바른 방식 — ErrorCategory 기반 매핑
return CATEGORY_STATUS_MAP.get(errorCode.getCategory());
```
```

**DomainException에 args Map 추가**:
```java
// domain 모듈
public abstract class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> args;

    protected DomainException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    protected DomainException(ErrorCode errorCode, Map<String, Object> args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = args != null ? Collections.unmodifiableMap(args) : Map.of();
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public Map<String, Object> getArgs() { return args; }
}

// 도메인별 예외 — args Map 활용
public class InventoryExhaustedException extends DomainException {
    public InventoryExhaustedException(Long inventoryId, int available, int requested) {
        super(
            InventoryErrorCode.INVENTORY_EXHAUSTED,
            Map.of(
                "inventoryId", inventoryId,
                "available", available,
                "requested", requested
            )
        );
    }
}
```

**왜 userMessage와 debugMessage를 분리하는가**: 사용자에게 "inventory_id=123, available=0"을 보여주면 시스템 내부 구조가 노출된다. userMessage("예약할 수 없습니다")는 사용자 친화적으로, debugMessage는 개발자가 원인을 빠르게 파악할 수 있도록 상세하게 작성한다. ErrorMapper가 이 변환을 전담하므로 Controller는 에러 처리를 전혀 신경 쓰지 않는다.

---

### API-DOC-001: Swagger/SpringDoc 어노테이션 [MINOR]

Controller에 SpringDoc 어노테이션을 추가하여 API 문서를 자동 생성한다.

```java
@RestController
@RequestMapping("/api/v1/extranet/properties")
@Tag(name = "Extranet - 숙소 관리", description = "파트너용 숙소 등록/관리 API")
@RequiredArgsConstructor
public class ExtranetPropertyController {

    @Operation(summary = "숙소 등록", description = "파트너가 새로운 숙소를 등록합니다")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "파트너를 찾을 수 없음")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> registerProperty(
            @Valid @RequestBody RegisterPropertyApiRequest request) {
        // ...
    }
}
```

**왜**: SpringDoc은 코드에서 API 문서를 자동 생성한다. 별도 문서를 수동 관리하면 코드와 문서가 불일치하게 된다.

---

### API-TST-001: API 테스트 전략 [MAJOR]

MockMvc + `@WebMvcTest`로 Controller 단위 테스트를 수행한다. UseCase를 Mock하여 HTTP 요청/응답만 검증한다.

```java
@WebMvcTest(ExtranetPropertyController.class)
class ExtranetPropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegisterPropertyUseCase registerPropertyUseCase;

    @Test
    void 숙소_등록_성공시_201_응답() throws Exception {
        // given
        given(registerPropertyUseCase.execute(any()))
            .willReturn(1L);

        // when & then
        mockMvc.perform(post("/api/v1/extranet/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "partnerId": 1,
                        "name": "서울 호텔",
                        "propertyTypeCode": "HOTEL",
                        "address": "서울시 강남구"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void 필수_필드_누락시_400_응답() throws Exception {
        mockMvc.perform(post("/api/v1/extranet/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "partnerId": 1
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.userMessage").value("입력값이 올바르지 않습니다"));
    }
}
```

**왜 MockMvc인가**: Controller 단위 테스트에서는 HTTP 파싱, 검증, 응답 포맷만 검증하면 충분하다. 전체 Spring Context를 띄우지 않아 빠르다. E2E 테스트는 별도로 `@SpringBootTest` + Testcontainers로 수행한다.

---

## 네이밍 컨벤션 요약

| 파일명 패턴 | 용도 | 비고 |
|------------|------|------|
| `{Domain}Controller.java` | REST Controller | UseCase만 의존 |
| `{Action}{Domain}ApiRequest.java` | Request DTO | record, Jakarta Validation |
| `{Domain}ApiResponse.java` | Response DTO | record |
| `{Domain}ApiMapper.java` | API ↔ Application DTO 변환 | static 메서드 |
| `ErrorMapper.java` | DomainException → 에러 응답 변환 | userMessage + debugMessage |

---

## API 엔드포인트 + Controller 매핑

### Extranet API (파트너용)

| HTTP | 엔드포인트 | Controller | UseCase |
|------|-----------|------------|---------|
| POST | /api/v1/extranet/properties | ExtranetPropertyController | RegisterPropertyUseCase |
| POST | /api/v1/extranet/properties/{id}/rooms | ExtranetRoomTypeController | RegisterRoomTypeUseCase |
| PUT | /api/v1/extranet/inventory | ExtranetInventoryController | SetInventoryUseCase |
| GET | /api/v1/extranet/reservations | ExtranetReservationController | (ReadManager 경유 별도 UseCase) |

### Customer API (고객용)

| HTTP | 엔드포인트 | Controller | UseCase |
|------|-----------|------------|---------|
| GET | /api/v1/search/properties | SearchPropertyController | SearchPropertyUseCase |
| GET | /api/v1/properties/{id}/rates | RateController | FetchRateUseCase |
| POST | /api/v1/reservations | ReservationController | CreateReservationUseCase |
| PATCH | /api/v1/reservations/{id}/cancel | ReservationController | CancelReservationUseCase |

### Admin API (관리자용)

| HTTP | 엔드포인트 | Controller | UseCase |
|------|-----------|------------|---------|
| GET | /api/v1/admin/properties | AdminPropertyController | (별도 Admin UseCase) |
| GET | /api/v1/admin/reservations | AdminReservationController | (별도 Admin UseCase) |

---

## 패키지 구조

```
adapter-in/rest-api/
├── config/
│   └── WebConfig.java
│
├── common/
│   ├── ApiResponse.java                     ← 공통 응답 래퍼 (core 모듈로 이동 가능)
│   ├── SliceResponse.java                   ← 페이지네이션 래퍼
│   ├── ErrorMapper.java                     ← DomainException → 에러 응답 변환
│   └── GlobalExceptionHandler.java          ← 전역 예외 처리기
│
├── extranet/
│   ├── ExtranetPropertyController.java
│   ├── ExtranetRoomTypeController.java
│   ├── ExtranetInventoryController.java
│   ├── ExtranetReservationController.java
│   ├── dto/
│   │   ├── RegisterPropertyApiRequest.java
│   │   ├── RegisterRoomTypeApiRequest.java
│   │   ├── SetInventoryApiRequest.java
│   │   └── PropertyApiResponse.java
│   └── mapper/
│       ├── PropertyApiMapper.java
│       ├── RoomTypeApiMapper.java
│       └── InventoryApiMapper.java
│
├── customer/
│   ├── SearchPropertyController.java
│   ├── RateController.java
│   ├── ReservationController.java
│   ├── dto/
│   │   ├── SearchPropertyApiRequest.java
│   │   ├── FetchRateApiRequest.java
│   │   ├── CreateReservationApiRequest.java
│   │   ├── PropertySummaryApiResponse.java
│   │   ├── RateDateApiResponse.java
│   │   └── ReservationApiResponse.java
│   └── mapper/
│       ├── SearchPropertyApiMapper.java
│       ├── RateApiMapper.java
│       └── ReservationApiMapper.java
│
└── admin/
    ├── AdminPropertyController.java
    ├── AdminReservationController.java
    ├── dto/
    │   └── AdminPropertyApiResponse.java
    └── mapper/
        └── AdminPropertyApiMapper.java
```

---

## ArchUnit 테스트로 강제할 규칙

| 규칙 | 검증 내용 |
|------|-----------|
| Controller UseCase 의존 | Controller는 *UseCase 인터페이스만 의존 (Service 직접 의존 금지) |
| Controller 트랜잭션 금지 | Controller 클래스에 @Transactional 사용 금지 |
| DTO는 Record | dto/ 패키지의 클래스는 record 타입 |
| API 계층 비즈니스 로직 금지 | Controller에서 Domain 클래스 직접 의존 금지 (UseCase를 통해서만 접근) |
| ApiMapper 존재 필수 | 각 Controller 패키지에 *ApiMapper 클래스 존재 필수 |
