# Application 레이어 컨벤션

## 원칙
- Application 레이어는 **흐름 조립**을 담당한다. 비즈니스 판단은 Domain, 외부 통신은 Adapter의 책임.
- UseCase 인터페이스로 외부(Controller)에 계약을 노출하고, Service가 이를 구현한다.
- Port 인터페이스는 Application에 정의한다. Domain에 Port를 두지 않는다.
- **Service에 @Transactional을 선언하지 않는다.** 트랜잭션 경계는 Manager 레이어에서 관리한다.

### 왜 이렇게 하는가
헥사고날 아키텍처에서 Application은 "오케스트레이터"다. Domain의 비즈니스 규칙을 조합하고, Port를 통해 외부 시스템(DB, Redis, 외부 API)과 통신한다.

Service에 @Transactional을 걸지 않는 이유: 하나의 UseCase가 여러 트랜잭션으로 나뉠 수 있다. 예를 들어 예약 생성 흐름은 "Redis 재고 차감 → DB 저장 → 외부 API 호출"로 구성되는데, Service에 트랜잭션을 걸면 전부 하나의 트랜잭션에 묶여 DB 커넥션을 불필요하게 오래 점유한다. Manager 레이어로 트랜잭션 경계를 분리하면 각 단계가 필요한 만큼만 커넥션을 사용한다.

---

## 레이어 구조

```
Controller → UseCase(Port-In) → Service → Manager → Port(Port-Out) → Adapter
```

| 구성 요소 | 역할 | @Transactional |
|-----------|------|:--------------:|
| Service | 오케스트레이션. Manager 조합. UseCase 구현체 | ❌ 금지 |
| {Domain}CommandManager | 쓰기 작업. persist, 상태 변경 | ✅ 메서드 단위 필수 |
| {Domain}ReadManager | 읽기 작업. 조회 전용 | ✅ 메서드 단위 readOnly=true |
| {Domain}ClientManager | 외부 API/Redis 호출 | ❌ 없음 |
| {UseCase}Validator | UseCase별 검증 전용. ReadManager 주입 | ❌ 없음 |
| {Domain}Factory | 도메인 객체 생성 (TimeProvider 주입) | ❌ 없음 |
| {Domain}PersistenceFacade | 여러 Port를 하나의 트랜잭션에서 묶어 호출 | ✅ 필수 |

---

## 규칙 목록

### APP-UC-001: UseCase 인터페이스 정의 [BLOCKER]

외부(Controller)가 호출하는 진입점은 UseCase 인터페이스로 정의한다. UseCase는 비즈니스 유스케이스 1개에 대응하며, 메서드는 1~2개로 제한한다.

```java
// 네이밍: {동사}{도메인}UseCase
public interface RegisterPropertyUseCase {
    Long execute(RegisterPropertyCommand command);
}

public interface SearchPropertyUseCase {
    PropertySliceResult execute(SearchPropertyQuery query);
}
```

**왜**: Controller가 구체 Service에 직접 의존하면 Application 내부 구조 변경이 Controller에 전파된다. UseCase 인터페이스를 두면 계약이 안정적으로 유지되고, 테스트에서 Mock 교체가 쉽다.

---

### APP-SVC-001: Service — UseCase 구현체 (트랜잭션 없음) [BLOCKER]

UseCase 인터페이스와 Service 구현체는 1:1로 매핑한다. Service는 **오케스트레이션만** 담당하며 `@Transactional`을 선언하지 않는다.

```java
@Service
@RequiredArgsConstructor
public class RegisterPropertyService implements RegisterPropertyUseCase {

    private final PartnerReadManager partnerReadManager;
    private final PropertyFactory propertyFactory;
    private final PropertyPersistenceFacade propertyPersistenceFacade;

    @Override
    public Long execute(RegisterPropertyCommand command) {
        // 1. 파트너 존재 확인 (ReadManager — readOnly 트랜잭션)
        partnerReadManager.getById(command.partnerId());

        // 2. 도메인 생성 (Factory — 트랜잭션 없음)
        Property property = propertyFactory.create(command);

        // 3. 저장 (PersistenceFacade — 쓰기 트랜잭션)
        return propertyPersistenceFacade.persist(property);
    }
}
```

**왜**: Service에 @Transactional이 없으므로 각 Manager 호출이 독립된 트랜잭션으로 실행된다. Redis 호출, 외부 API 호출이 DB 트랜잭션에 묶이지 않아 커넥션 점유 시간이 최소화된다.

---

### APP-MGR-001: Manager 레이어 규칙 [BLOCKER]

Manager는 트랜잭션 경계를 담당하는 중간 레이어다. 역할에 따라 3종류로 나뉜다.

**@Transactional은 반드시 메서드 단위로 선언한다. 클래스 레벨 @Transactional은 금지한다.**

```java
// CommandManager — 쓰기 작업, @Transactional 메서드 단위 필수
@Component
@RequiredArgsConstructor
public class ReservationCommandManager {

    private final ReservationCommandPort reservationCommandPort;

    @Transactional
    public Long persist(Reservation reservation) {
        return reservationCommandPort.persist(reservation);
    }

    @Transactional
    public void persistAll(List<Reservation> reservations) {
        reservationCommandPort.persistAll(reservations);
    }
}

// ReadManager — 읽기 작업, @Transactional(readOnly=true) 메서드 단위 필수
@Component
@RequiredArgsConstructor
public class PropertyReadManager {

    private final PropertyQueryPort propertyQueryPort;

    @Transactional(readOnly = true)
    public Property getById(PropertyId id) {
        return propertyQueryPort.findById(id)
            .orElseThrow(PropertyNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public void verifyExists(PropertyId id) {
        if (!propertyQueryPort.existsById(id)) {
            throw new PropertyNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public SliceResult<Property> findByCondition(PropertySliceCriteria criteria) {
        return propertyQueryPort.findByCondition(criteria);
    }
}

// ClientManager — 외부 호출, 트랜잭션 없음
@Component
@RequiredArgsConstructor
public class InventoryClientManager {

    private final InventoryRedisPort inventoryRedisPort;

    public void decrementStock(Long roomTypeId, List<LocalDate> dates) {
        inventoryRedisPort.decrementStock(roomTypeId, dates);
    }

    public void incrementStock(Long roomTypeId, List<LocalDate> dates) {
        inventoryRedisPort.incrementStock(roomTypeId, dates);
    }
}
```

**왜 Manager를 분리하는가**: Port를 Service에서 직접 호출하면 트랜잭션 없이 DB에 접근하게 된다. Manager가 Port를 감싸면서 트랜잭션을 보장한다. 또한 ReadManager의 readOnly 트랜잭션은 JPA flush를 생략하고, DB 복제 라우팅(읽기 전용 DB)에 활용할 수 있다.

**왜 메서드 단위인가**: 클래스 레벨 @Transactional을 걸면 트랜잭션이 필요 없는 메서드가 추가될 때 불필요한 트랜잭션이 열린다. 또한 메서드마다 전파 속성(REQUIRES_NEW 등)을 다르게 설정할 수 있어 유연성이 확보된다.

---

### APP-FCD-001: PersistenceFacade — 영속화 묶음 [BLOCKER]

여러 CommandPort를 하나의 트랜잭션에서 묶어야 할 때 PersistenceFacade를 사용한다.

```java
@Component
@Transactional
@RequiredArgsConstructor
public class ReservationPersistenceFacade {

    private final ReservationCommandPort reservationCommandPort;
    private final InventoryCommandPort inventoryCommandPort;

    public Long persistReservationWithItems(
            Reservation reservation, 
            List<Inventory> inventories) {
        // 같은 트랜잭션에서 예약 + 재고 기록 저장
        Long reservationId = reservationCommandPort.persist(reservation);
        inventoryCommandPort.persistAll(inventories);
        return reservationId;
    }
}
```

**왜**: Service에 @Transactional이 없으므로, 여러 Aggregate를 원자적으로 저장해야 할 때 PersistenceFacade가 트랜잭션 경계를 제공한다. CommandManager는 단일 Port만 감싸고, PersistenceFacade는 여러 Port를 하나의 트랜잭션으로 묶는 역할이다.

---

### APP-BC-001: BC 간 경계 규칙 [BLOCKER]

Bounded Context 간 호출에는 엄격한 규칙이 있다. 잘못된 의존은 순환 참조와 트랜잭션 오염을 유발한다.

| 대상 | 같은 BC | 다른 BC |
|------|:------:|:------:|
| UseCase / Service | ❌ | ❌ |
| ReadManager | ✅ | ✅ |
| Validator | ✅ | ✅ (ReadManager는 다른 BC에서도 호출 가능하므로) |
| CommandManager | ✅ | ❌ |
| Factory + PersistenceFacade | ✅ | ✅ (쓰기 필요 시) |
| ClientManager | ✅ | ✅ |
| Port (직접) | ❌ | ❌ |

```java
// 허용 — 다른 BC의 ReadManager 호출 (읽기 전용, 부작용 없음)
@Service
@RequiredArgsConstructor
public class CreateReservationService implements CreateReservationUseCase {

    private final PropertyReadManager propertyReadManager;      // 다른 BC — 읽기 OK
    private final ReservationFactory reservationFactory;         // 같은 BC
    private final ReservationPersistenceFacade reservationPersistenceFacade; // 같은 BC

    @Override
    public Long execute(CreateReservationCommand command) {
        // 다른 BC ReadManager 호출 — 숙소 존재 확인
        propertyReadManager.getById(PropertyId.of(command.propertyId()));
        // ...
    }
}

// 금지 — UseCase(Port-In)를 내부에서 호출
@Service
public class SomeService {
    private final RegisterPropertyUseCase registerPropertyUseCase; // 금지! 순환 의존
}

// 금지 — Port 직접 호출
@Service
public class SomeService {
    private final PropertyCommandPort propertyCommandPort; // 금지! 트랜잭션 없이 DB 접근
}

// 허용 — 다른 BC의 쓰기가 필요하면 대상 도메인의 Factory + PersistenceFacade 사용
@Service
public class SyncSupplierService implements SyncSupplierUseCase {
    private final PropertyFactory propertyFactory;                    // 다른 BC Factory
    private final PropertyPersistenceFacade propertyPersistenceFacade; // 다른 BC Facade
}
```

**왜 UseCase 호출을 금지하는가**: UseCase(Port-In)를 내부에서 호출하면 A → B → A 순환 의존이 발생한다. ReadManager는 읽기 전용이라 부작용이 없으므로 어디서든 안전하게 호출할 수 있다. Port 직접 호출을 금지하는 이유는 트랜잭션 없이 DB에 접근하는 것을 방지하기 위함이다. 반드시 Manager를 경유해야 한다.

---

### APP-OBX-001: Outbox 패턴 — 이벤트 대체 [BLOCKER]

Spring ApplicationEventPublisher 사용을 금지한다. 크로스 도메인 비동기 처리는 Outbox 테이블 + 스케줄러로 처리한다.

```java
// 금지 — Spring Event
@Component
public class ReservationEventPublisher {
    @Autowired private ApplicationEventPublisher publisher;
    
    public void publish(ReservationCreatedEvent event) {
        publisher.publishEvent(event); // 금지!
    }
}

// 허용 — Outbox 테이블에 저장 (같은 트랜잭션)
@Component
@Transactional
@RequiredArgsConstructor
public class ReservationPersistenceFacade {

    private final ReservationCommandPort reservationCommandPort;
    private final OutboxCommandPort outboxCommandPort;

    public Long persistWithOutbox(Reservation reservation, OutboxMessage outbox) {
        Long id = reservationCommandPort.persist(reservation);
        outboxCommandPort.persist(outbox); // 같은 트랜잭션 — 원자성 보장
        return id;
    }
}
```

**스케줄러 2개 구조**:

| 스케줄러 | 역할 | 주기 |
|---------|------|------|
| 메인 스케줄러 | PENDING → PROCESSING → 처리 → COMPLETED | 짧은 주기 (예: 5초) |
| 좀비 복구 스케줄러 | PROCESSING 상태 + 타임아웃 초과 → PENDING 복구 | 긴 주기 (예: 1분) |

```java
// Outbox 상태 (domain/common)
public enum OutboxStatus {
    PENDING,      // 대기 중
    PROCESSING,   // 처리 중
    COMPLETED,    // 완료
    FAILED        // 최종 실패 (재시도 초과)
}

// 메인 스케줄러
@Component
@RequiredArgsConstructor
public class OutboxMainScheduler {

    private final OutboxReadManager outboxReadManager;
    private final OutboxCommandManager outboxCommandManager;
    private final OutboxProcessor outboxProcessor;

    @Scheduled(fixedDelay = 5000)
    public void process() {
        List<Outbox> pendings = outboxReadManager.findPending(100);
        for (Outbox outbox : pendings) {
            outboxCommandManager.markProcessing(outbox.getId());
            try {
                outboxProcessor.execute(outbox);
                outboxCommandManager.markCompleted(outbox.getId());
            } catch (Exception e) {
                outboxCommandManager.markFailed(outbox.getId());
            }
        }
    }
}

// 좀비 복구 스케줄러
@Component
@RequiredArgsConstructor
public class OutboxZombieScheduler {

    private final OutboxCommandManager outboxCommandManager;

    @Scheduled(fixedDelay = 60000)
    public void recover() {
        // PROCESSING 상태에서 N초 이상 경과한 건 → PENDING 복구
        outboxCommandManager.recoverStaleProcessing(Duration.ofMinutes(5));
    }
}
```

**왜 이벤트 대신 Outbox인가**: Spring ApplicationEvent는 메모리 기반이라 장애 시 유실된다. 이벤트 발행 + 누락 보정 스케줄러 2개를 운영하면 "정상 경로(이벤트) + 보정 경로(스케줄러)"가 공존하여 디버깅이 복잡해진다. Outbox는 정상 경로가 스케줄러 1개뿐이므로 흐름이 단순하고, DB 트랜잭션으로 원자성이 보장된다.

---

### APP-DTO-001: Command/Query DTO는 Record + Domain VO 필드 [BLOCKER]

Application 레이어의 Command/Query DTO는 반드시 Java record로 선언한다. 인스턴스 메서드는 금지하며 정적 팩토리 메서드(of)만 허용한다.

**필드 타입은 `Long`, `String` 같은 원시 타입 대신 Domain VO(`PartnerId`, `PropertyTypeId`, `PropertyName` 등)를 사용한다.** VO 변환은 Adapter-in(Controller)에서 ApiMapper가 한 번만 수행한다. Application 레이어 내부에서는 항상 VO로 다룬다.

```java
// Command — 상태 변경 요청 (필드에 Domain VO 사용)
public record RegisterPropertyCommand(
    PartnerId partnerId,
    PropertyTypeId propertyTypeId,
    PropertyName name,
    PropertyDescription description,
    Location location,
    PromotionText promotionText
) {
    public static RegisterPropertyCommand of(PartnerId partnerId, PropertyTypeId propertyTypeId, ...) {
        return new RegisterPropertyCommand(partnerId, propertyTypeId, ...);
    }
}

// Query — 조회 요청
public record SearchPropertyQuery(
    String region,
    LocalDate checkIn,
    LocalDate checkOut,
    int guests,
    int size,
    Long cursor
) {}
```

**Adapter-in에서의 변환:**
```java
// Controller에서 ApiMapper로 원시 타입 -> VO 변환
public static RegisterPropertyCommand toCommand(RegisterPropertyApiRequest request) {
    return RegisterPropertyCommand.of(
        PartnerId.of(request.partnerId()),
        PropertyTypeId.of(request.propertyTypeId()),
        PropertyName.of(request.name()),
        ...
    );
}
```

**왜**: Record는 불변성을 언어 수준에서 보장한다. Command/Query가 mutable이면 Service 실행 중 값이 바뀔 위험이 있다. 인스턴스 메서드를 금지하여 DTO가 순수 데이터 컨테이너 역할만 하도록 강제한다.

**왜 Domain VO를 사용하는가**: Application 레이어 내부에서는 항상 VO로 다루는 것이 일관적이다. `Long partnerId`를 Service, Factory, Validator 곳곳에서 `PartnerId.of(command.partnerId())`로 변환하면 변환 코드가 산재한다. Command 시점에 이미 VO이면 변환은 Adapter-in에서 한 번만 일어나고, Application 레이어 전체에서 타입 안전성이 보장된다.

---

### APP-PRT-001: Port 정의 — CommandPort / QueryPort 분리 [BLOCKER]

Persistence Port는 CQRS 원칙에 따라 Command와 Query를 분리한다.

```java
// CommandPort — 저장 전용
public interface PropertyCommandPort {
    Long persist(Property property);           // 신규 저장, ID 반환
    void persistAll(List<Property> properties); // 벌크 저장
    // delete 메서드 없음 — 모든 삭제는 soft delete (DeletionStatus VO 활용)
}

// QueryPort — 조회 전용
public interface PropertyQueryPort {
    Optional<Property> findById(PropertyId id);
    boolean existsById(PropertyId id);  // Validator용 존재 확인 (엔티티 로딩 없음)
    SliceResult<Property> findByCondition(PropertySliceCriteria criteria);
}
```

**규칙**:
- QueryPort에 `boolean existsById(ID id)` 메서드를 선언한다. ReadManager의 `verifyExists()` 내부에서 호출하며, 엔티티 전체를 로딩하지 않는다.
- CommandPort: `persist` / `persistAll`만 허용. update/delete 메서드 금지.
- hard delete 없음. 모든 삭제는 soft delete — Domain의 `DeletionStatus` VO를 활용하여 비즈니스 메서드(`delete(Instant)`)로 상태만 변경한 뒤 persist로 저장한다.
- QueryPort: `findAll()` (전체 조회) 금지. 반드시 조건부/페이징 조회만 선언.
- Domain → Entity 변환은 Adapter에서 처리. persist에 Domain 객체를 전달한다.

**왜**: Command에서는 JPA의 persist/merge로 충분하다. 신규(id == null)면 persist, 기존이면 merge — Adapter에서 판단한다. findAll은 데이터가 늘어나면 OOM을 유발하므로 원천 차단한다. hard delete를 금지하는 이유는 데이터 이력 추적이 불가능해지고, 외래 키 참조 무결성이 깨질 수 있기 때문이다.

---

### APP-PRT-002: Port 파라미터 규칙 [BLOCKER]

| Port 유형 | 파라미터 타입 | 반환 타입 |
|-----------|-------------|-----------|
| Persistence CommandPort | Domain 객체 (Aggregate, VO) | ID (Long) 또는 void |
| Persistence QueryPort | Domain VO (ID VO, Criteria) | Domain 객체 |
| Client Port (외부 API) | Application DTO (record) | Application DTO (record) |

```java
// Persistence Port — Domain 객체 사용
public interface PropertyCommandPort {
    Long persist(Property property);  // Domain Aggregate
}

public interface PropertyQueryPort {
    Optional<Property> findById(PropertyId id);  // Domain ID VO
    SliceResult<Property> findByCondition(PropertySliceCriteria criteria);  // Domain Criteria
}

// Client Port — Application DTO 사용
public interface SupplierClient {
    SupplierPropertyResponse fetchProperties(SupplierFetchRequest request);
    SupplierRateResponse fetchRates(SupplierRateFetchRequest request);
}
```

**왜**: Persistence Port가 Application DTO를 받으면 Adapter가 Application 내부 구조에 결합된다. Domain 객체(VO, Criteria)로 전달하면 Adapter는 Domain 언어만 이해하면 된다. 반면 Client Port(외부 API)에 Domain Aggregate를 전달하면 외부 Adapter가 Domain 내부 구조에 결합되므로, Application DTO로 변환하여 전달한다.

---

### APP-PRT-003: Client Port에 Domain Aggregate 직접 전달 금지 [BLOCKER]

외부 시스템 연동용 Client Port에 Domain Aggregate를 직접 전달하지 않는다.

```java
// 금지 — Domain Aggregate 직접 전달
public interface SupplierClient {
    void syncProperty(Property property);  // Domain Aggregate 노출
}

// 허용 — Application DTO로 변환 후 전달
public interface SupplierClient {
    void syncProperty(SupplierPropertySyncRequest request);  // Application DTO
}
```

**왜**: Domain Aggregate가 외부 Adapter까지 전파되면, Domain 내부 구조 변경이 외부 연동 코드에 영향을 미친다. Application DTO로 경계를 만들면 Domain과 외부 시스템이 독립적으로 변경 가능하다.

---

### APP-FAC-001: Factory — TimeProvider 일원화 [BLOCKER]

`Instant.now()` / `LocalDateTime.now()` / `LocalDate.now()` 직접 호출을 금지한다 (ArchUnit으로 강제). TimeProvider는 Factory에만 주입한다. Service, Manager에서 TimeProvider를 직접 주입/호출하지 않는다.

```java
// core 모듈에 정의
public interface TimeProvider {
    Instant now();
    LocalDate today();
}

// infra 모듈에 구현
@Component
public class SystemTimeProvider implements TimeProvider {
    @Override
    public Instant now() { return Instant.now(); }

    @Override
    public LocalDate today() { return LocalDate.now(); }
}

// Factory에서 TimeProvider 사용
@Component
@RequiredArgsConstructor
public class ReservationFactory {

    private final TimeProvider timeProvider;

    public Reservation create(CreateReservationCommand command) {
        Instant now = timeProvider.now();
        return Reservation.forNew(
            command.ratePlanId(),
            command.guestName(),
            command.guestPhone(),
            command.guestEmail(),
            command.checkIn(),
            command.checkOut(),
            command.guestCount(),
            command.totalAmount(),
            now
        );
    }
}

// 금지 — Service/Manager에서 TimeProvider 사용
@Service
public class SomeService {
    private final TimeProvider timeProvider; // 금지!
}

// 테스트용
public class FixedTimeProvider implements TimeProvider {
    private final Instant fixed;
    public FixedTimeProvider(Instant fixed) { this.fixed = fixed; }

    @Override
    public Instant now() { return fixed; }

    @Override
    public LocalDate today() { return fixed.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(); }
}
```

**왜**: TimeProvider를 Factory에 집중시키면 "시간이 필요한 곳 = Factory"로 단일화된다. Service/Manager 곳곳에서 TimeProvider를 주입하면 시간 생성 지점이 분산되어 테스트에서 제어가 어렵고, 코드 리뷰 시 누락을 잡기 힘들다. Factory에서 Instant를 받아 Domain의 forNew()에 전달하는 것으로 충분하다.

---

### APP-EXC-001: 도메인별 구체 예외 사용 [BLOCKER]

`IllegalArgumentException`, `IllegalStateException` 등 범용 예외를 던지지 않는다. 도메인별 구체 예외 클래스를 사용한다.

```java
// 금지
if (partner == null) {
    throw new IllegalArgumentException("파트너를 찾을 수 없습니다");
}

// 허용
if (partner == null) {
    throw new PartnerNotFoundException();
}
```

**왜**: 범용 예외는 GlobalExceptionHandler에서 적절한 HTTP 상태 코드로 매핑할 수 없다. `PartnerNotFoundException`이면 404, `InventoryExhaustedException`이면 409로 명확히 매핑된다.

---

### APP-VAL-001: 검증 로직 위치 [MAJOR]

| 검증 유형 | 위치 | 예시 |
|-----------|------|------|
| 단일 필드 검증 | Domain VO (Compact Constructor) | name이 blank인지 |
| 비즈니스 불변 조건 | Domain Aggregate (forNew, 비즈니스 메서드) | baseOccupancy <= maxOccupancy |
| 존재 여부 확인 (조회 불필요) | Validator (ReadManager.verifyExists) | partnerId가 DB에 존재하는지 (APP-VAL-002) |
| 존재 여부 확인 (조회 필요) | ReadManager (getById) | 조회된 엔티티를 이후 로직에서 사용해야 할 때 |
| 크로스 도메인 검증 | Service (Validator 또는 ReadManager 조합) | 해당 RoomType이 해당 Property 소속인지 |

```java
@Override
public Long execute(RegisterRoomTypeCommand command) {
    // 존재 여부 — ReadManager에서
    propertyReadManager.getById(PropertyId.of(command.propertyId()));

    // 비즈니스 검증 — Domain에서 (forNew 내부)
    RoomType roomType = RoomType.forNew(
        command.propertyId(),
        command.name(),
        command.baseOccupancy(),
        command.maxOccupancy()
    );

    return roomTypeCommandManager.persist(roomType);
}
```

**왜**: 검증 책임을 명확히 분리해야 중복 검증이 없고, 검증 누락도 방지된다. "이 데이터가 유효한가"는 Domain, "이 데이터가 존재하는가"는 Validator(또는 ReadManager).

---

### APP-VAL-002: UseCase별 Validator — ReadManager 주입 [BLOCKER]

UseCase별 검증이 필요할 때 `{UseCase명}Validator` 전용 컴포넌트를 만든다. Validator는 **ReadManager를 주입**하며, `verifyExists()` 메서드로 존재 여부만 확인한다. ReadManager 내부에서 QueryPort의 `existsById()`를 호출하고 트랜잭션을 관리한다.

**Validator는 Port(QueryPort)를 직접 주입하지 않는다.** 모든 Application 컴포넌트(Service, Manager, Validator)가 Port 직접 사용 금지 규칙을 따른다. Port 접근은 반드시 Manager를 경유한다.

**Validator에는 @Transactional을 선언하지 않는다.** ReadManager가 이미 메서드 단위로 트랜잭션을 관리하므로, Validator에서 추가 트랜잭션을 걸면 ReadManager와 역할이 중복된다.

```java
// ReadManager에 verifyExists() 추가 — 존재 확인 + 없으면 예외
@Component
@RequiredArgsConstructor
public class PartnerReadManager {

    private final PartnerQueryPort partnerQueryPort;

    @Transactional(readOnly = true)
    public Partner getById(PartnerId id) {
        return partnerQueryPort.findById(id)
            .orElseThrow(PartnerNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public void verifyExists(PartnerId id) {
        if (!partnerQueryPort.existsById(id)) {
            throw new PartnerNotFoundException();
        }
    }
}

// Validator — ReadManager 주입, @Transactional 없음
@Component
@RequiredArgsConstructor
public class PropertyRegistrationValidator {

    private final PartnerReadManager partnerReadManager;
    private final PropertyTypeReadManager propertyTypeReadManager;

    public void validate(RegisterPropertyCommand command) {
        partnerReadManager.verifyExists(command.partnerId());
        propertyTypeReadManager.verifyExists(command.propertyTypeId());
    }
}
```

**Service에서 Validator 사용:**
```java
@Service
@RequiredArgsConstructor
public class RegisterPropertyService implements RegisterPropertyUseCase {

    private final PropertyRegistrationValidator validator;
    private final PropertyFactory propertyFactory;
    private final PropertyCommandManager propertyCommandManager;

    @Override
    public Long execute(RegisterPropertyCommand command) {
        // 1. 검증 (Validator — ReadManager.verifyExists 경유)
        validator.validate(command);

        // 2. 도메인 생성 (Factory — TimeProvider)
        Property property = propertyFactory.createProperty(command);

        // 3. 저장 (CommandManager — @Transactional)
        return propertyCommandManager.persist(property);
    }
}
```

**규칙:**
- Validator 네이밍: `{UseCase명}Validator` (예: `PropertyRegistrationValidator`, `ReservationCreationValidator`)
- Validator는 `@Component`로 선언하고, **@Transactional을 선언하지 않는다** (ReadManager가 트랜잭션 관리)
- ReadManager의 `verifyExists()` 메서드를 사용하여 존재 여부를 확인한다. ReadManager 내부에서 QueryPort의 `existsById()`를 호출한다
- Validator는 Port(QueryPort)를 직접 주입하지 않는다. 반드시 ReadManager를 경유한다
- 검증 실패 시 도메인 구체 예외(PartnerNotFoundException 등)는 ReadManager의 `verifyExists()`가 던진다

**왜 Validator가 QueryPort를 직접 주입하면 안 되는가**: QueryPort는 인터페이스이므로 트랜잭션이 없다. Validator가 직접 `@Transactional`을 걸면 ReadManager와 역할이 중복되고, 트랜잭션 관리 책임이 두 곳에 분산된다. ReadManager에 `verifyExists()` 메서드를 추가하면 트랜잭션 관리는 Manager가 일관되게 담당하고, Validator는 "어떤 검증을 어떤 순서로 조합할지"만 담당한다.

**APP-VAL-001과의 관계**: ReadManager에 `getById()`(조회 필요 시)와 `verifyExists()`(존재 확인만)를 모두 두어, 용도에 따라 적절한 메서드를 선택한다. Validator는 `verifyExists()`를, Service에서 조회된 엔티티를 이후 로직에 사용해야 하면 `getById()`를 호출한다.

---

## 네이밍 컨벤션 요약

| 파일명 패턴 | 용도 | 비고 |
|------------|------|------|
| `{Action}{Domain}UseCase.java` | Port-In 인터페이스 | 메서드 1~2개 |
| `{Action}{Domain}Service.java` | UseCase 구현체 | @Transactional 금지 |
| `{Domain}CommandManager.java` | 쓰기 작업 | @Transactional 필수 |
| `{Domain}ReadManager.java` | 읽기 작업 | @Transactional(readOnly=true) |
| `{Domain}ClientManager.java` | 외부 호출 (Redis, API) | 트랜잭션 없음 |
| `{UseCase명}Validator.java` | UseCase별 검증 전용 | ReadManager 주입, @Transactional 없음 |
| `{Domain}Coordinator.java` | 여러 Manager를 조합하는 복합 흐름 조율 | 복잡한 오케스트레이션 |
| `{Domain}Processor.java` | 단일 작업 단위 처리 | 배치, 변환 등 |
| `{Domain}Executor.java` | 특정 실행 로직 캡슐화 | 명확한 단일 실행 |
| `{Domain}Factory.java` | 생성 팩토리 | TimeProvider 주입 |
| `{Domain}PersistenceFacade.java` | 영속화 묶음 | 여러 Port 트랜잭션 묶음 |
| `{Domain}CommandPort.java` | Persistence 쓰기 Port | persist/persistAll만 |
| `{Domain}QueryPort.java` | Persistence 읽기 Port | findById, findByCondition |
| `{Domain}Client.java` | Client Port | 외부 API 연동 |
| `{Action}{Domain}Command.java` | Command DTO | record |
| `{Domain}Result.java` | Response DTO | record |

---

## 복합 흐름 예시 — 예약 생성

```java
@Service
@RequiredArgsConstructor
public class CreateReservationService implements CreateReservationUseCase {

    private final PropertyReadManager propertyReadManager;          // 다른 BC — 읽기
    private final InventoryClientManager inventoryClientManager;    // Redis 재고
    private final ReservationFactory reservationFactory;            // Factory
    private final ReservationPersistenceFacade reservationFacade;   // PersistenceFacade

    @Override
    public Long execute(CreateReservationCommand command) {
        // 1. 숙소 존재 확인 (다른 BC ReadManager)
        propertyReadManager.getById(PropertyId.of(command.propertyId()));

        // 2. Redis 재고 차감 (ClientManager — 트랜잭션 밖)
        inventoryClientManager.decrementStock(command.roomTypeId(), command.dates());

        try {
            // 3. 도메인 생성 (Factory — TimeProvider)
            Reservation reservation = reservationFactory.create(command);

            // 4. DB 저장 (PersistenceFacade — 쓰기 트랜잭션)
            return reservationFacade.persistReservationWithItems(
                reservation, command.toInventoryUpdates());
        } catch (Exception e) {
            // 5. 실패 시 Redis 재고 복구
            inventoryClientManager.incrementStock(command.roomTypeId(), command.dates());
            throw e;
        }
    }
}
```

---

## 패키지 구조

최상위를 **역할(port, service, manager, ...)**이 아닌 **BC(Bounded Context)**로 나눈다. Domain 패키지와 대칭을 이루며, BC 내부에서 역할별 하위 패키지를 둔다.

### 핵심 원칙

1. **최상위 = BC**: `property/`, `reservation/`, `inventory/`, `partner/`, `supplier/` 등
2. **manager는 플랫**: `manager/` 하위에 `command/`, `read/` 서브패키지를 두지 않는다. `PropertyCommandManager`와 `PropertyReadManager`가 같은 `manager/` 디렉토리에 공존한다.
3. **port/out은 플랫**: `port/out/` 하위에 `persistence/` 서브패키지를 두지 않는다. 향후 `redis/`, `client/` 어댑터가 추가되면 그때 서브패키지를 도입한다.
4. **공통 컴포넌트**: `common/` 하위에 `TimeProvider` 등 BC에 속하지 않는 인프라성 컴포넌트를 둔다.

### 왜 BC 단위인가

플랫 구조(`application/service/`, `application/manager/command/`)에서는 서로 다른 BC의 컴포넌트가 한 디렉토리에 혼재한다. 파일이 늘어나면 특정 BC의 전체 구조를 파악하기 어렵고, BC 간 의존 방향을 패키지 레벨에서 제어할 수 없다. BC 단위로 나누면 "이 BC의 UseCase, Port, Manager, Factory가 무엇인지"를 디렉토리 하나에서 즉시 확인할 수 있다.

### 왜 manager를 플랫하게 두는가

`command/`, `read/` 서브패키지는 파일 수가 적을 때 오히려 네비게이션을 방해한다. 네이밍 컨벤션(`{Domain}CommandManager`, `{Domain}ReadManager`)으로 역할이 명확히 구분되므로, 서브패키지 없이도 혼동이 없다.

```
application/
├── property/                              ← Property BC
│   ├── port/
│   │   ├── in/                            ← UseCase 인터페이스
│   │   │   ├── RegisterPropertyUseCase.java
│   │   │   ├── AddPropertyPhotosUseCase.java
│   │   │   ├── SetPropertyAmenitiesUseCase.java
│   │   │   ├── SetPropertyAttributesUseCase.java
│   │   │   └── SearchPropertyUseCase.java
│   │   └── out/                           ← Adapter 인터페이스 (플랫)
│   │       ├── PropertyCommandPort.java
│   │       ├── PropertyQueryPort.java
│   │       ├── PropertyAmenityCommandPort.java
│   │       ├── PropertyPhotoCommandPort.java
│   │       └── PropertyAttributeValueCommandPort.java
│   ├── dto/
│   │   ├── command/
│   │   │   ├── RegisterPropertyCommand.java
│   │   │   ├── AddPropertyPhotosCommand.java
│   │   │   ├── SetPropertyAmenitiesCommand.java
│   │   │   └── SetPropertyAttributesCommand.java
│   │   ├── query/
│   │   │   └── SearchPropertyQuery.java
│   │   └── response/
│   │       └── PropertySliceResult.java
│   ├── service/
│   │   ├── RegisterPropertyService.java
│   │   ├── AddPropertyPhotosService.java
│   │   ├── SetPropertyAmenitiesService.java
│   │   ├── SetPropertyAttributesService.java
│   │   └── SearchPropertyService.java
│   ├── manager/                           ← 플랫 (command/read 나누지 않음)
│   │   ├── PropertyCommandManager.java
│   │   ├── PropertyReadManager.java
│   │   ├── PropertyPhotoCommandManager.java
│   │   ├── PropertyAmenityCommandManager.java
│   │   └── PropertyAttributeValueCommandManager.java
│   ├── validator/
│   │   ├── PropertyRegistrationValidator.java
│   │   └── PropertyExistenceValidator.java
│   ├── factory/
│   │   └── PropertyFactory.java
│   └── facade/
│       └── PropertyPersistenceFacade.java
│
├── roomtype/                              ← RoomType BC
│   ├── port/
│   │   ├── in/
│   │   │   └── RegisterRoomTypeUseCase.java
│   │   └── out/
│   │       ├── RoomTypeCommandPort.java
│   │       └── RoomTypeQueryPort.java
│   ├── dto/command/
│   │   └── RegisterRoomTypeCommand.java
│   ├── service/
│   │   └── RegisterRoomTypeService.java
│   ├── manager/
│   │   ├── RoomTypeCommandManager.java
│   │   └── RoomTypeReadManager.java
│   └── validator/
│       └── RoomTypeRegistrationValidator.java
│
├── pricing/                               ← Pricing BC
│   ├── port/
│   │   ├── in/
│   │   │   └── FetchRateUseCase.java
│   │   └── out/
│   │       ├── RatePlanCommandPort.java
│   │       ├── RateCommandPort.java
│   │       └── RateQueryPort.java
│   ├── dto/
│   │   ├── query/
│   │   │   └── FetchRateQuery.java
│   │   └── response/
│   │       └── RateDateResult.java
│   ├── service/
│   │   └── FetchRateService.java
│   └── manager/
│       └── RateCacheClientManager.java
│
├── inventory/                             ← Inventory BC
│   ├── port/
│   │   ├── in/
│   │   │   └── SetInventoryUseCase.java
│   │   └── out/
│   │       ├── InventoryCommandPort.java
│   │       ├── InventoryQueryPort.java
│   │       └── InventoryRedisPort.java
│   ├── dto/command/
│   │   └── SetInventoryCommand.java
│   ├── service/
│   │   └── SetInventoryService.java
│   ├── manager/
│   │   ├── InventoryCommandManager.java
│   │   ├── InventoryReadManager.java
│   │   └── InventoryClientManager.java
│   └── facade/
│       └── InventoryPersistenceFacade.java
│
├── reservation/                           ← Reservation BC
│   ├── port/
│   │   ├── in/
│   │   │   ├── CreateReservationUseCase.java
│   │   │   └── CancelReservationUseCase.java
│   │   └── out/
│   │       ├── ReservationCommandPort.java
│   │       └── ReservationQueryPort.java
│   ├── dto/command/
│   │   └── CreateReservationCommand.java
│   ├── service/
│   │   ├── CreateReservationService.java
│   │   └── CancelReservationService.java
│   ├── manager/
│   │   ├── ReservationCommandManager.java
│   │   └── ReservationReadManager.java
│   ├── validator/
│   │   └── ReservationCreationValidator.java
│   ├── factory/
│   │   └── ReservationFactory.java
│   └── facade/
│       └── ReservationPersistenceFacade.java
│
├── partner/                               ← Partner BC
│   ├── port/out/
│   │   └── PartnerQueryPort.java
│   └── manager/
│       └── PartnerReadManager.java
│
├── propertytype/                          ← PropertyType BC
│   ├── port/out/
│   │   └── PropertyTypeQueryPort.java
│   └── manager/
│       └── PropertyTypeReadManager.java
│
├── supplier/                              ← Supplier BC
│   ├── port/
│   │   ├── in/
│   │   │   └── SyncSupplierUseCase.java
│   │   └── out/
│   │       ├── SupplierCommandPort.java
│   │       └── SupplierClient.java
│   ├── dto/
│   │   ├── command/
│   │   │   └── SyncSupplierCommand.java
│   │   └── response/
│   │       └── SyncResult.java
│   ├── service/
│   │   └── SyncSupplierService.java
│   └── manager/
│       └── SupplierClientManager.java
│
├── outbox/                                ← Outbox (공통 인프라)
│   ├── port/out/
│   │   └── OutboxCommandPort.java
│   ├── manager/
│   │   ├── OutboxCommandManager.java
│   │   └── OutboxReadManager.java
│   ├── factory/
│   │   └── OutboxFactory.java
│   └── scheduler/
│       ├── OutboxMainScheduler.java
│       ├── OutboxZombieScheduler.java
│       └── OutboxProcessor.java
│
└── common/                                ← 공통 (BC에 속하지 않는 컴포넌트)
    └── factory/
        └── TimeProvider.java
```

---

## ArchUnit 테스트로 강제할 규칙

| 규칙 | 검증 내용 |
|------|-----------|
| UseCase 1:1 Service | Service 클래스는 정확히 1개의 UseCase 인터페이스만 구현 |
| Port 정의 위치 | *Port, *UseCase 인터페이스는 application 패키지에만 존재 |
| DTO는 Record | dto/ 패키지의 클래스는 record 타입 |
| Service @Transactional 금지 | Service 클래스에 @Transactional 어노테이션 금지 |
| CommandManager @Transactional 메서드 단위 | *CommandManager 클래스에 클래스 레벨 @Transactional 금지, public 메서드에 @Transactional 필수 |
| ReadManager readOnly 메서드 단위 | *ReadManager 클래스에 클래스 레벨 @Transactional 금지, public 메서드에 @Transactional(readOnly=true) 필수 |
| ClientManager 트랜잭션 금지 | *ClientManager 클래스에 @Transactional 어노테이션 금지 |
| Validator @Transactional 금지 | *Validator 클래스에 @Transactional 어노테이션 금지 (ReadManager가 트랜잭션 관리) |
| Validator Port 직접 주입 금지 | *Validator 클래스에서 *Port 직접 의존 금지, ReadManager만 의존 허용 |
| Service 의존성 | Service는 UseCase, Manager, Factory, PersistenceFacade, Validator만 의존 (Port 직접 의존 금지) |
| Instant.now() 금지 | application 패키지에서 Instant.now(), LocalDateTime.now(), LocalDate.now() 직접 호출 금지 |
| TimeProvider Factory 전용 | TimeProvider는 *Factory 클래스에서만 의존 가능 |
| ApplicationEventPublisher 금지 | application 패키지에서 ApplicationEventPublisher 사용 금지 |
| Port 직접 호출 금지 | Service에서 *Port 인터페이스 직접 의존 금지 (Manager/Facade/Validator 경유) |
