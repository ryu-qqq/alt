# 도메인 레이어 컨벤션

## 원칙
- Domain 레이어는 순수 Java로 유지한다. Spring, JPA 등 외부 프레임워크 의존 금지.
- Port, Service는 Domain에 두지 않는다. Application 레이어의 책임.
- 도메인 모델이 비즈니스 규칙을 코드로 표현해야 한다. 조건문 나열 대신 타입과 메서드로 정책을 드러낸다.

### 왜 이렇게 하는가
AI 시대에 코드 양은 비용이 아니다. 중요한 건 **강한 컨벤션이 일관되게 유지되는 것**이다. 사람이 바뀌어도, AI가 코드를 생성해도 컨벤션을 벗어나면 ArchUnit 테스트가 깨지게 하여 품질을 강제한다.

Port를 Domain이 아닌 Application에 두는 이유: Domain은 "내가 뭘 할 수 있는가"만 알면 되고, "외부에 뭘 요청하는가"는 Application의 관심사다. 이렇게 하면 Domain 모듈은 어떤 인터페이스도 없이 순수 비즈니스 모델만 담게 된다.

---

## 규칙 목록

### DOM-AGG-001: Aggregate 팩토리 메서드 패턴 [BLOCKER]

Aggregate는 생성자를 직접 노출하지 않는다. static 팩토리 메서드를 사용한다.

| 메서드 | 용도 | 비즈니스 검증 |
|--------|------|:------------:|
| `forNew(...)` | 신규 생성. ID는 null(DB 채번) | O |
| `reconstitute(...)` | DB 복원. 모든 필드를 받음 | X |

```java
public class Property {
    
    private final PropertyId id;
    private String name;
    private PropertyType propertyType;
    // ...
    
    // 외부 생성자 금지
    private Property(PropertyId id, String name, PropertyType propertyType, ...) {
        this.id = id;
        this.name = name;
        this.propertyType = propertyType;
    }
    
    // 신규 생성 — 비즈니스 검증 수행
    public static Property forNew(String name, PropertyType propertyType, ...) {
        // 검증 로직
        return new Property(null, name, propertyType, ...);
    }
    
    // DB 복원 — 검증 없이 그대로 복원
    public static Property reconstitute(PropertyId id, String name, PropertyType propertyType, ...) {
        return new Property(id, name, propertyType, ...);
    }
}
```

**왜**: 생성 의도(신규 vs 복원)가 메서드명에서 바로 드러난다. reconstitute에서 비즈니스 검증을 하지 않는 이유는 — DB에 이미 저장된 데이터는 저장 시점에 이미 검증을 통과한 것이므로 복원할 때 다시 검증하면 불필요한 비용이고, 과거 규칙이 바뀌었을 때 기존 데이터 복원이 실패하는 문제가 생긴다.

---

### DOM-AGG-002: Aggregate Root ID는 ID VO [BLOCKER]

Aggregate Root의 ID는 원시 타입(Long) 대신 전용 ID VO를 사용한다. 하위 엔티티는 Long 허용.

```java
public record PropertyId(Long value) {
    public static PropertyId of(Long value) {
        return new PropertyId(value);
    }
}
```

**isNew() 판단**: `id.value() == null`이면 신규 (persist), 아니면 기존 (merge).

**Aggregate Root만 적용하는 이유**: 모든 엔티티에 ID VO를 만들면 보일러플레이트가 과도해진다. Aggregate Root는 외부에서 참조하는 식별자이므로 타입 안전성이 중요하지만, 내부 하위 엔티티(ReservationItem, RateOverride 등)는 외부 노출이 적어 Long으로 충분하다.

**적용 대상**: PropertyId, RoomTypeId, RatePlanId, InventoryId, ReservationId, PartnerId, SupplierId

---

### DOM-AGG-004: Setter 금지 + 비즈니스 메서드 [BLOCKER]

`setXxx()` Setter 메서드를 사용하지 않는다. 상태 변경은 비즈니스 의미가 담긴 메서드를 통해서만 가능하다.

```java
// 금지
property.setName("새 이름");
property.setStatus(PropertyStatus.INACTIVE);

// 허용
property.rename("새 이름");
property.deactivate();
reservation.cancel("고객 요청");
```

**왜**: Setter는 "무엇이 왜 바뀌는지" 의도가 없다. `cancel(reason)`은 "취소한다"는 비즈니스 행위가 명확하고, 취소에 필요한 부가 로직(상태 검증, 타임스탬프 갱신 등)을 메서드 안에 캡슐화할 수 있다.

---

### DOM-AGG-010: equals/hashCode ID 기반 + 불변 필드 final [MAJOR]

equals/hashCode는 ID 필드만 기반으로 구현한다. 생성 이후 변경되지 않는 필드(id, createdAt)는 final 선언.

```java
public class Property {
    private final PropertyId id;
    private final Instant createdAt;
    private String name;  // 변경 가능 — final 아님
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Property p)) return false;
        return id != null && id.equals(p.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

**왜**: Entity의 동등성은 속성이 아닌 식별자로 판단한다. 이름이 바뀌어도 같은 Property다. `id != null` 체크는 forNew() 직후 id가 null인 케이스를 방어한다.

---

### DOM-ID-001: ID VO 기본 구조 [BLOCKER]

`{Domain}Id` Record로 정의. `of()` 정적 팩토리 메서드 제공.

```java
public record PropertyId(Long value) {
    public static PropertyId of(Long value) {
        return new PropertyId(value);
    }
}

public record ReservationId(Long value) {
    public static ReservationId of(Long value) {
        return new ReservationId(value);
    }
}
```

**왜**: `Long propertyId`와 `Long roomTypeId`는 타입이 같아서 실수로 바꿔 넣어도 컴파일러가 못 잡는다. ID VO를 쓰면 `PropertyId`와 `RoomTypeId`는 다른 타입이므로 컴파일 시점에 잡힌다.

---

### DOM-VO-001: VO는 Record + of() + Compact Constructor [BLOCKER]

Value Object는 Java Record로 정의한다. of() 정적 팩토리 메서드를 제공하고, Compact Constructor에서 검증한다.

```java
public record Location(
    String address,
    double latitude,
    double longitude,
    String neighborhood,
    String region
) {
    // Compact Constructor — 검증
    public Location {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소는 필수입니다");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("위도 범위 초과");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("경도 범위 초과");
        }
    }
    
    public static Location of(String address, double lat, double lng, String neighborhood, String region) {
        return new Location(address, lat, lng, neighborhood, region);
    }
}
```

**왜**: Record는 불변성을 언어 수준에서 보장한다. Compact Constructor에서 생성 시점에 검증하므로 "유효하지 않은 VO"가 존재할 수 없다.

---

### DOM-VO-002: Enum VO displayName() 권장 [MAJOR]

Enum 타입 VO는 사용자 표시용 `displayName()` 메서드를 제공한다.

```java
public enum PropertyType {
    HOTEL("호텔"),
    MOTEL("모텔"),
    PENSION("펜션"),
    RESORT("리조트"),
    GUESTHOUSE("게스트하우스"),
    HOSTEL("호스텔");
    
    private final String displayName;
    
    PropertyType(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
}
```

**왜**: API 응답이나 UI에서 코드값 대신 사용자가 읽을 수 있는 이름이 필요하다. displayName()을 도메인에서 제공하면 표현 계층에서 별도 매핑 로직이 필요 없다.

---

### DOM-CRI-001: Criteria 기본 구조 [BLOCKER]

조회 조건은 `{Domain}SliceCriteria` 또는 `{Domain}PageCriteria` Record로 정의한다.

```java
public record PropertySliceCriteria(
    PropertyType propertyType,
    Integer minStarRating,
    String neighborhood,
    int size,
    Long cursor
) {
    public static PropertySliceCriteria of(PropertyType type, Integer minStar, String neighborhood, int size, Long cursor) {
        return new PropertySliceCriteria(type, minStar, neighborhood, size, cursor);
    }
}
```

**왜 Domain에 있는가**: Criteria는 QueryPort(Application)의 파라미터로 쓰인다. Port가 Application에 있지만, 파라미터 타입은 Domain 객체를 사용한다. 이렇게 하면 Persistence Adapter가 Application의 내부 DTO에 의존하지 않고 Domain 언어로 조회 조건을 받게 된다.

---

### DOM-ERR-001: ErrorCode 인터페이스 — HTTP 상태코드 분리 [BLOCKER]

도메인별 ErrorCode enum은 공통 ErrorCode 인터페이스를 구현한다.
**ErrorCode에 HTTP 상태코드를 포함하지 않는다** — HTTP는 API 레이어의 관심사이다.
대신 **ErrorCategory**를 통해 에러의 의미론적 분류를 제공한다. API 레이어는 이 카테고리를 기반으로 HTTP 상태코드를 결정한다.

```java
// 에러 카테고리 (domain 모듈)
public enum ErrorCategory {
    NOT_FOUND,    // 리소스 없음
    VALIDATION,   // 입력값/비즈니스 규칙 위반
    CONFLICT,     // 상태 충돌 (중복, 동시성 등)
    FORBIDDEN     // 금지된 행위 (권한, 상태 전이 불가 등)
}

// 공통 인터페이스 (domain 모듈)
public interface ErrorCode {
    String getCode();              // "{DOMAIN}-{NUMBER}" 형식
    String getMessage();           // 사용자 메시지
    ErrorCategory getCategory();   // 에러 카테고리 — API 레이어의 HTTP 상태 매핑 근거
}

// 도메인별 구현 (domain 모듈)
public enum AccommodationErrorCode implements ErrorCode {
    PROPERTY_NOT_FOUND("ACC-001", "숙소를 찾을 수 없습니다", ErrorCategory.NOT_FOUND),
    INVALID_STAR_RATING("ACC-002", "유효하지 않은 성급입니다", ErrorCategory.VALIDATION),
    ROOM_TYPE_NOT_FOUND("ACC-003", "객실 유형을 찾을 수 없습니다", ErrorCategory.NOT_FOUND);
    
    private final String code;
    private final String message;
    private final ErrorCategory category;
    
    AccommodationErrorCode(String code, String message, ErrorCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }
    
    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
    @Override public ErrorCategory getCategory() { return category; }
}

// HTTP 매핑은 API 레이어에서 처리 (adapter-in/rest-api)
// ErrorCategory → HttpStatus 변환은 ErrorMapper/GlobalExceptionHandler가 담당
```

**왜 HTTP를 분리하는가**: 도메인 레이어가 HTTP를 알면 안 된다. 그러나 에러 메시지 문자열이나 코드 접두사에 의존하여 HTTP 상태를 결정하면 리팩토링에 취약하다. ErrorCategory는 도메인이 "이 에러의 성격이 무엇인지"를 선언적으로 표현하고, API 레이어는 그 카테고리에 따라 일관된 HTTP 상태코드를 결정한다. 이렇게 하면 에러 메시지를 변경해도 HTTP 매핑이 깨지지 않는다.

**ErrorCategory 선택 가이드**:
| 카테고리 | 용도 | 예시 |
|----------|------|------|
| NOT_FOUND | 요청한 리소스가 존재하지 않음 | 숙소 없음, 객실 없음, 예약 없음 |
| VALIDATION | 입력값이 잘못되었거나 비즈니스 규칙 위반 | 유효하지 않은 성급, 잘못된 날짜 범위 |
| CONFLICT | 현재 상태와 충돌하는 요청 | 재고 부족, 이미 취소된 예약 재취소 |
| FORBIDDEN | 금지된 행위 (상태 전이 불가, 권한 부족 등) | 완료된 예약 취소 시도 |

---

### DOM-EXC-001: Exception 기본 구조 [BLOCKER]

DomainException(RuntimeException)을 상속한다. Checked Exception 사용 금지.

```java
// 공통 기반 (core 모듈)
public abstract class DomainException extends RuntimeException {
    private final ErrorCode errorCode;
    
    protected DomainException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

// 도메인별 예외
public class PropertyNotFoundException extends DomainException {
    public PropertyNotFoundException() {
        super(AccommodationErrorCode.PROPERTY_NOT_FOUND);
    }
}
```

**왜 Unchecked**: Checked Exception은 메서드 시그니처를 오염시키고, 대부분의 도메인 예외는 호출자가 복구할 수 없는 상황이다. RuntimeException으로 통일하고 글로벌 예외 처리기에서 일괄 처리한다.

---

### DOM-CMN-002: 외부 레이어 의존 금지 (DIP) [BLOCKER]

Domain 모듈은 다른 어떤 모듈에도 의존하지 않는다. core 모듈만 의존 허용.

```
허용: domain → core (공통 인터페이스, 유틸)
금지: domain → application, adapter-*, infra, spring-*, jakarta-*
```

**ArchUnit으로 강제한다.**

---

### 시간 필드 규칙 (DOM-TIME)

| 용도 | Java 타입 | DB 타입 | timezone 변환 |
|------|-----------|---------|:-------------:|
| 시점 (언제 일어났는가) | Instant | TIMESTAMP (UTC) | O |
| 비즈니스 날짜 (현지 기준) | LocalDate | DATE | X |
| 비즈니스 시간 (현지 기준) | LocalTime | TIME | X |

**왜 분리하는가**: "4/2 체크인"은 숙소 현지 시간 4/2다. Instant로 저장하면 timezone 변환 시 날짜가 밀릴 수 있다. LocalDate는 timezone 개념 없이 "그냥 4/2"이므로 이 문제가 없다. 반면 "예약 생성 시각"은 전 세계 어디서 봐도 같은 시점이어야 하므로 Instant(UTC)가 맞다.

---

## 패키지 구조

```
domain/
├── accommodation/
│   ├── Property.java                ← Aggregate Root
│   ├── PropertyId.java              ← ID VO
│   ├── RoomType.java
│   ├── RoomTypeId.java
│   ├── RatePlan.java
│   ├── RatePlanId.java
│   ├── RatePlanAddOn.java
│   ├── RateRule.java
│   ├── RateOverride.java
│   ├── Location.java                ← VO (Record)
│   ├── PropertyType.java            ← Enum VO
│   ├── PropertyStatus.java
│   ├── PaymentPolicy.java
│   ├── SourceType.java
│   ├── PropertySliceCriteria.java   ← Criteria (Record)
│   └── AccommodationErrorCode.java  ← ErrorCode enum
│
├── inventory/
│   ├── Inventory.java
│   ├── InventoryId.java
│   └── InventoryErrorCode.java
│
├── reservation/
│   ├── Reservation.java
│   ├── ReservationId.java
│   ├── ReservationItem.java
│   ├── ReservationStatus.java
│   └── ReservationErrorCode.java
│
├── partner/
│   ├── Partner.java
│   ├── PartnerId.java
│   ├── PartnerMember.java
│   └── PartnerErrorCode.java
│
└── supplier/
    ├── Supplier.java
    ├── SupplierId.java
    ├── SupplierProperty.java
    ├── SupplierRoomType.java
    └── SupplierErrorCode.java
```

---

## ArchUnit 테스트로 강제할 규칙

| 규칙 | 검증 내용 |
|------|-----------|
| 외부 의존 금지 | domain 패키지는 spring, jakarta, jpa, hibernate 등 import 금지 |
| Setter 금지 | domain 패키지의 클래스에 set으로 시작하는 public 메서드 금지 |
| 생성자 비공개 | Aggregate 클래스에 public 생성자 금지 (private/protected만) |
| VO는 Record | domain 패키지의 *Id, *Criteria, Location 등은 record 타입 |
| ErrorCode 구현 | *ErrorCode enum은 ErrorCode 인터페이스 구현 필수 |
| Exception 상속 | domain 패키지의 *Exception은 DomainException 상속 필수 |
| Instant.now() 금지 | domain 패키지에서 Instant.now(), LocalDateTime.now(), LocalDate.now() 직접 호출 금지 |

---

## 공통 VO / 유틸 (domain/common)

프로젝트 전반에서 재사용하는 공통 타입을 `domain/common` 패키지에 정의한다. 이 패키지는 특정 BC에 속하지 않는 범용 VO와 인터페이스를 담는다.

### DOM-CMN-010: Query / Pagination [BLOCKER]

조회 관련 공통 타입은 다음 구조를 따른다.

```java
// offset 기반 페이지 요청
public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0) throw new IllegalArgumentException("page는 0 이상이어야 합니다");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size는 1~100이어야 합니다");
    }
    
    public long offset() {
        return (long) page * size;
    }
}

// 커서 기반 페이지 요청 (제네릭 커서)
public record CursorPageRequest<C>(C cursor, int size) {
    public CursorPageRequest {
        if (size < 1 || size > 100) throw new IllegalArgumentException("size는 1~100이어야 합니다");
    }
}

// 정렬 + 페이징 묶음
public record QueryContext<K extends SortKey>(
    K sortKey,
    SortDirection direction,
    int size,
    Long cursor
) {}

// 응답 메타 — 오프셋 기반
public record PageMeta(int page, int size, long totalElements, int totalPages) {}

// 응답 메타 — 커서 기반
public record SliceMeta(boolean hasNext, Long nextCursor) {}
```

**왜**: 모든 BC에서 페이지네이션 요청/응답 구조가 제각각이면 API 일관성이 깨진다. 공통 타입으로 통일하면 Controller, QueryPort, QueryDslRepository 전 계층에서 같은 어휘를 사용한다.

---

### DOM-CMN-011: Sorting / Filtering [BLOCKER]

정렬과 필터링에 사용하는 인터페이스를 정의한다. 각 BC는 enum으로 구현한다.

```java
// 정렬 키 인터페이스 — 각 BC가 enum으로 구현
public interface SortKey {
    String fieldName();
}

// 정렬 방향
public enum SortDirection {
    ASC, DESC
}

// 검색 필드 인터페이스 — 각 BC가 enum으로 구현
public interface SearchField {
    String fieldName();
}

// 날짜 필드 인터페이스
public interface DateField {
    String fieldName();
}

// BC별 구현 예시
public enum PropertySortKey implements SortKey {
    CREATED_AT("createdAt"),
    NAME("name"),
    STAR_RATING("starRating");
    
    private final String fieldName;
    PropertySortKey(String fieldName) { this.fieldName = fieldName; }
    @Override public String fieldName() { return fieldName; }
}
```

**왜**: 정렬/필터 키를 String으로 받으면 오타를 컴파일러가 잡지 못한다. enum으로 제한하면 허용된 정렬 키만 사용할 수 있고, QueryDSL에서 동적 정렬 매핑이 타입 안전하게 된다.

---

### DOM-CMN-012: Value Objects — 공통 [BLOCKER]

프로젝트 전반에서 사용하는 공통 VO를 정의한다.

```java
// 날짜 범위 (체크인~체크아웃)
public record DateRange(LocalDate startDate, LocalDate endDate) {
    public DateRange {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("시작일과 종료일은 필수입니다");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 뒤여야 합니다");
        }
    }
    
    public long nights() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
    
    public List<LocalDate> dates() {
        return startDate.datesUntil(endDate).toList();
    }
}

// 금액 (BigDecimal 기반 — 다통화, 소수점 지원)
public record Money(BigDecimal amount) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다");
        }
    }
    
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }
    
    public static Money of(int amount) {
        return new Money(BigDecimal.valueOf(amount));
    }
    
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }
    
    public Money multiply(int factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
    }
    
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
}

// Soft Delete 상태
public record DeletionStatus(boolean deleted, Instant deletedAt) {
    public static DeletionStatus active() {
        return new DeletionStatus(false, null);
    }
    
    public static DeletionStatus deleted(Instant deletedAt) {
        return new DeletionStatus(true, deletedAt);
    }
}
```

**왜 BigDecimal인가**: 해외 Supplier 연동 시 달러, 엔 등 소수점이 있는 통화를 다룰 수 있다. BigDecimal은 부동소수점 오차가 없어 금융/결제 계산에 적합하다. `of(int)` 팩토리도 제공하여 원화 사용 시 편의성을 유지한다.

---

### DOM-CMN-013: Cache / Lock 키 인터페이스 [MAJOR]

Redis 캐시 키와 분산 락 키를 타입 안전하게 관리한다.

```java
// 캐시 키 인터페이스
public interface CacheKey {
    String key();
    long ttlSeconds();
}

// 분산 락 키 인터페이스
public interface LockKey {
    String key();
    long waitTimeMillis();
    long leaseTimeMillis();
}
```

**왜**: 캐시 키를 String으로 조합하면 키 충돌, 오타, TTL 불일치가 발생한다. 인터페이스로 정의하면 키 형식과 TTL이 코드에서 드러나고, 새로운 캐시 항목 추가 시 반드시 TTL을 고려하게 된다.

---

### 공통 패키지 구조

```
domain/
├── common/
│   ├── query/
│   │   ├── PageRequest.java
│   │   ├── CursorPageRequest.java
│   │   ├── QueryContext.java
│   │   ├── PageMeta.java
│   │   └── SliceMeta.java
│   ├── sort/
│   │   ├── SortKey.java              ← 인터페이스
│   │   ├── SortDirection.java        ← enum
│   │   ├── SearchField.java          ← 인터페이스
│   │   └── DateField.java            ← 인터페이스
│   ├── vo/
│   │   ├── DateRange.java
│   │   ├── Money.java
│   │   └── DeletionStatus.java
│   └── infra/
│       ├── CacheKey.java             ← 인터페이스
│       └── LockKey.java              ← 인터페이스
├── accommodation/              ← 숙소, 객실, 브랜드, 편의시설, 사진, EAV
│   └── ...
├── pricing/                    ← 요금 정책, 요금 규칙, Add-on, Rate
│   └── ...
├── location/                   ← 랜드마크, PropertyLandmark 매핑
│   └── ...
├── inventory/                  ← 재고 (미구현)
│   └── ...
├── reservation/                ← 예약 (미구현)
│   └── ...
├── partner/                    ← 파트너 ID VO
│   └── ...
└── supplier/                   ← 외부 공급자 (미구현)
    └── ...
```

---

## 네이밍 컨벤션 요약

| 파일명 패턴 | 용도 | 비고 |
|------------|------|------|
| `{Domain}.java` | Aggregate Root | 핵심 도메인 모델 |
| `{Domain}Id.java` | 자기 ID VO | null 허용 (isNew 판단), Record |
| `{참조대상}Id.java` | 참조 ID VO | null 불허, Record |
| `{Domain}Name.java` | String VO | Record |
| `{Domain}Status.java` | 상태 Enum | displayName() 권장 |
| `{Domain}Type.java` | 유형 Enum | displayName() 권장 |
| `{Domain}ErrorCode.java` | ErrorCode enum | ErrorCode 인터페이스 구현 |
| `{Domain}Exception.java` | 도메인 예외 | DomainException 상속 |
| `{Domain}SliceCriteria.java` | 조회 조건 | Record |

---

## 추가 컨벤션 (GitHub Issues에서 도출)

### DOM-VO-003: String VO에 MAX_LENGTH 검증 필수 [MAJOR]

String을 래핑하는 VO는 DDL의 VARCHAR 길이에 맞는 `MAX_LENGTH` 상수를 선언하고 검증해야 한다.

```java
public record PartnerName(String value) {
    private static final int MAX_LENGTH = 200;  // partner.name VARCHAR(200)

    public PartnerName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("파트너명은 필수입니다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("파트너명은 " + MAX_LENGTH + "자 이하여야 합니다");
        }
    }
}
```

**왜**: DB에서 잘리는 것보다 도메인에서 먼저 거부하는 게 안전하다. DDL과 도메인의 제약이 일치해야 한다.

---

### DOM-VO-004: ID VO에 forNew() 팩토리 제공 [MAJOR]

ID VO는 `of(Long)` 외에 신규 생성 의도를 명시하는 `forNew()` 팩토리를 제공한다.

```java
public record PropertyId(Long value) {
    public static PropertyId of(Long value) { return new PropertyId(value); }
    public static PropertyId forNew() { return new PropertyId(null); }
    public boolean isNew() { return value == null; }
}
```

**왜**: `of(null)`은 "새 엔티티 생성"이라는 의도가 불명확하다. `forNew()`로 의미를 드러낸다.

---

### DOM-ENUM-001: 상태 Enum에 전이 테이블 선언 [MAJOR]

상태 전이가 있는 Enum은 전이 테이블을 Enum 내부에 선언한다. Aggregate는 부수효과만 담당한다.

```java
public enum ReservationStatus {
    PENDING("대기"),
    CONFIRMED("확정"),
    CANCELLED("취소"),
    COMPLETED("완료"),
    NO_SHOW("노쇼");

    private static final Map<ReservationStatus, Set<ReservationStatus>> TRANSITIONS = Map.of(
        PENDING,    Set.of(CONFIRMED, CANCELLED),
        CONFIRMED,  Set.of(COMPLETED, NO_SHOW, CANCELLED)
    );

    public boolean canTransitTo(ReservationStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public ReservationStatus transitTo(ReservationStatus target) {
        if (!canTransitTo(target)) {
            throw new InvalidReservationStateException(this, target);
        }
        return target;
    }
}
```

Aggregate 코드:
```java
public void confirm() {
    this.status = status.transitTo(CONFIRMED);
}
```

**왜**: 전이 규칙이 각 메서드에 흩어지면 상태 머신 전체를 파악하기 어렵다. Enum에 집중시키면 한 곳에서 전이 테이블을 볼 수 있다.

---

### DOM-AGG-015: 도메인에 인프라 인터페이스 금지 [BLOCKER]

CacheKey, LockKey 등 인프라 관심사는 도메인 레이어에 두지 않는다.
캐시, 분산 락은 adapter-out 또는 application 레이어에서 정의한다.

**왜**: 도메인은 비즈니스 규칙만 표현한다. 캐시 키 패턴이나 락 전략은 인프라 결정이다.

---

### DOM-ERR-002: ErrorCode는 자기 BC만 담당 [MAJOR]

ErrorCode enum에 다른 BC의 에러코드를 포함하지 않는다.

```java
// 잘못된 예: AccommodationErrorCode에 pricing 관련 코드
RATE_PLAN_NOT_FOUND("ACC-003", "요금 정책을 찾을 수 없습니다")  // ← pricing BC 책임

// 올바른 예: 각 BC가 자기 에러코드만 관리
// AccommodationErrorCode → ACC-xxx
// PricingErrorCode → PRC-xxx
```
