# Persistence 레이어 컨벤션

## 원칙
- Persistence Adapter는 Application의 Port 인터페이스를 구현한다.
- CQRS를 엄격히 분리한다: Command는 JpaRepository, Query는 QueryDSL.
- JPA Entity와 Domain 모델은 완전히 분리한다. Mapper로 변환한다.
- Entity에 비즈니스 로직을 넣지 않는다. Entity는 순수 데이터 매핑 객체다.
- **JPA 관계 어노테이션을 전면 금지한다.** @OneToMany, @ManyToOne, @OneToOne, @ManyToMany 모두 금지. Aggregate 내부도 예외 없음.
- **hard delete 없음.** 모든 삭제는 soft delete (DeletionStatus VO 활용).

### 왜 이렇게 하는가
Domain 모델과 JPA Entity를 분리하면 Domain이 JPA 어노테이션에 오염되지 않고, DB 스키마 변경이 Domain에 전파되지 않는다. CQRS 분리로 Command(write)와 Query(read)의 데이터 접근 패턴을 독립적으로 최적화할 수 있다. Command는 JPA의 영속성 컨텍스트를 활용하고, Query는 QueryDSL로 필요한 컬럼만 정확히 조회한다.

관계 어노테이션을 Aggregate 내부에서도 금지하는 이유: Aggregate 내부라도 `@OneToMany`를 쓰면 Lazy Loading 트리거, N+1 문제, 영속성 컨텍스트 의존 등이 발생한다. Long FK 전략이면 이런 문제 자체가 불가능하고, 필요한 데이터는 QueryDSL JOIN으로 명시적으로 가져온다.

---

## 규칙 목록

### PER-ENT-001: JPA Entity 기본 규칙 [BLOCKER]

Entity 클래스명은 `{Domain}JpaEntity` 접미사를 사용한다. 비즈니스 로직을 넣지 않는다. **모든 JPA 관계 어노테이션 사용을 금지**하고, Long FK 전략을 사용한다.

**Entity 생성 패턴:**
- Lombok 사용 금지 (`@Getter`, `@Setter`, `@NoArgsConstructor` 등 — PER-ENT-004 참조)
- setter 전면 금지 (Mapper에서도 setter를 사용하지 않는다)
- `static create()` 팩토리 메서드가 **유일한 생성 진입점**
- `protected` 기본 생성자 (JPA 스펙 요구)
- `private` 전체 필드 생성자 (외부 접근 차단)
- getter는 수동 작성

```java
@Entity
@Table(name = "property")
public class PropertyJpaEntity extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long partnerId;
    private Long brandId;
    private Long propertyTypeId;
    private String name;
    private String description;
    private String address;
    private double latitude;
    private double longitude;
    private String neighborhood;
    private String region;
    private String status;
    private String promotionText;

    // JPA 스펙 요구
    protected PropertyJpaEntity() {
        super();
    }

    // 외부 접근 차단
    private PropertyJpaEntity(Long id, Long partnerId, Long brandId, Long propertyTypeId,
                               String name, String description, String address,
                               double latitude, double longitude,
                               String neighborhood, String region,
                               String status, String promotionText,
                               Instant createdAt, Instant updatedAt, Instant deletedAt) {
        super(createdAt, updatedAt, deletedAt);
        this.id = id;
        this.partnerId = partnerId;
        // ...
    }

    // 유일한 생성 진입점
    public static PropertyJpaEntity create(Long id, Long partnerId, Long brandId, Long propertyTypeId,
                                            String name, String description, String address,
                                            double latitude, double longitude,
                                            String neighborhood, String region,
                                            String status, String promotionText,
                                            Instant createdAt, Instant updatedAt, Instant deletedAt) {
        return new PropertyJpaEntity(id, partnerId, brandId, propertyTypeId,
            name, description, address, latitude, longitude,
            neighborhood, region, status, promotionText,
            createdAt, updatedAt, deletedAt);
    }

    // getter만 제공 — setter 없음
    public Long getId() { return id; }
    public Long getPartnerId() { return partnerId; }
    public Long getBrandId() { return brandId; }
    public Long getPropertyTypeId() { return propertyTypeId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getNeighborhood() { return neighborhood; }
    public String getRegion() { return region; }
    public String getStatus() { return status; }
    public String getPromotionText() { return promotionText; }

    // 비즈니스 로직 금지 — rename(), deactivate() 같은 메서드 없음
    // Entity는 데이터 매핑 전용
}
```

**금지 목록** (Aggregate 내부 포함):
```java
// 전부 금지
@OneToMany(mappedBy = "property", fetch = FetchType.LAZY)
private List<RoomTypeJpaEntity> roomTypes;  // 금지!

@ManyToOne(fetch = FetchType.LAZY)
private PropertyJpaEntity property;  // 금지!

@OneToOne
private LocationJpaEntity location;  // 금지!

@ManyToMany
private Set<AmenityJpaEntity> amenities;  // 금지!
```

**왜 Aggregate 내부도 예외 없는가**: `@OneToMany(fetch = LAZY)`로 선언해도 N+1 문제를 완전히 차단할 수 없다. `toString()`, JSON 직렬화, 실수로 getter 호출 등에서 Lazy Loading이 트리거된다. Long FK 전략이면 N+1 자체가 불가능하고, Aggregate 내부 하위 엔티티도 별도 QueryDSL 조회로 가져온다.

**왜 `{Domain}JpaEntity` 접미사인가**: Domain의 `Property`와 JPA의 `PropertyJpaEntity`를 이름으로 구분하여 혼동을 방지한다. import 시 충돌도 없다.

---

### PER-ENT-002: BaseAuditEntity 상속 [BLOCKER]

모든 Entity는 `BaseAuditEntity`를 상속하여 audit 필드를 표준화한다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseAuditEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

Soft Delete가 필요한 Entity는 `SoftDeletableEntity`를 상속한다:
```java
@MappedSuperclass
@Getter
public abstract class SoftDeletableEntity extends BaseAuditEntity {

    private boolean deleted;
    private Instant deletedAt;

    public boolean isDeleted() {
        return deleted;
    }
}
```

**왜**: audit 필드(createdAt, updatedAt)가 누락되면 데이터 이력 추적이 불가능하다. 상속으로 강제하면 개발자가 잊어버릴 수 없다. soft delete를 Entity 상속으로 제공하여 Domain의 `DeletionStatus` VO와 매핑한다.

---

### PER-ENT-003: persist/persistAll만 — hard delete 금지 [BLOCKER]

CommandPort에 delete 메서드를 정의하지 않는다. 모든 삭제는 soft delete로 처리한다.

```java
// 허용 — persist/persistAll만
public interface PropertyCommandPort {
    Long persist(Property property);
    void persistAll(List<Property> properties);
    // delete 메서드 없음!
}

// 삭제 흐름 예시
// 1. Domain에서 상태 변경
property.delete(now);  // DeletionStatus.deleted(now) 설정

// 2. persist로 저장 (merge — deleted=true, deletedAt=now)
propertyCommandPort.persist(property);
```

**왜**: hard delete는 데이터 이력 추적이 불가능하고, 외래 키 참조 무결성이 깨질 수 있다. soft delete면 데이터 복구가 가능하고, 삭제된 데이터를 포함한 이력 조회가 가능하다.

---

### PER-ENT-004: Entity Lombok 사용 금지 [BLOCKER]

Entity 클래스에서 Lombok 어노테이션 사용을 **전면 금지**한다. 모든 getter, 생성자는 수동으로 작성한다.

**금지 목록:**
```java
// 전부 금지
@Getter          // 금지 — 수동 getter 작성
@Setter          // 금지 — setter 자체가 금지
@NoArgsConstructor  // 금지 — protected 기본 생성자 수동 작성
@AllArgsConstructor // 금지 — private 전체 필드 생성자 수동 작성
@Builder         // 금지 — create() 팩토리 메서드 사용
@Data            // 금지 — @Getter + @Setter + @ToString 등 조합
@ToString        // 금지 — 필요 시 수동 작성
@EqualsAndHashCode // 금지 — 필요 시 수동 작성
```

**왜 Lombok을 금지하는가:**
1. **명시적 의도 표현**: 수동 작성하면 어떤 필드가 외부에 노출되는지, 어떤 생성자가 존재하는지 코드에서 바로 보인다.
2. **컴파일 타임 안전성**: 필드가 추가/변경되면 `create()` 시그니처와 getter가 함께 변경되어야 한다. Lombok은 이 변경을 자동으로 처리하여 Mapper 누락을 감지할 수 없다.
3. **setter 차단**: `@Data`나 `@Setter`를 실수로 추가하면 불변성이 깨진다. Lombok을 전면 금지하면 이런 실수 자체가 불가능하다.
4. **Mapper 동기화 신호**: Entity 필드 변경 시 `create()` 파라미터가 바뀌므로 Mapper도 반드시 함께 수정해야 한다는 컴파일 에러가 발생한다.

---

### PER-ENT-005: Entity 상태 변경 메서드 금지 [BLOCKER]

Entity에 `updateXxx()`, `changeXxx()` 같은 상태 변경 메서드를 두지 않는다. Entity는 순수 데이터 매핑 객체이므로 `create()` 팩토리, getter, `isXxx()` 판단 메서드만 허용한다.

```java
// 금지 — Entity에서 직접 상태 변경
@Entity
public class PropertyJpaEntity extends SoftDeletableEntity {
    public void updateName(String name) {  // 금지!
        this.name = name;
    }
    public void deactivate() {  // 금지!
        this.status = "INACTIVE";
    }
}

// 올바른 흐름
// 1. Domain에서 상태 변경
property.rename(PropertyName.of("새 이름"), now);

// 2. Mapper로 Entity 변환
PropertyJpaEntity entity = mapper.toEntity(property);

// 3. save (merge)
repository.save(entity);
```

**허용되는 것**: `create()` static 팩토리, getter, `isXxx()` 판단 메서드
**금지되는 것**: `setXxx()`, `updateXxx()`, `changeXxx()`, `deleteXxx()` 등 상태 변경 메서드

**왜**: 상태 변경은 반드시 Domain 객체에서 수행해야 한다. Entity에 변경 메서드가 있으면 Domain을 거치지 않고 Persistence에서 직접 상태를 바꿀 수 있어서 비즈니스 검증을 우회하게 된다. Entity는 순수 데이터 매핑 객체 — getter와 create()만 허용.

---

### PER-MAP-001: Mapper — Domain <-> Entity 변환 [BLOCKER]

Mapper는 `{Domain}EntityMapper` 클래스에서 `toDomain()` / `toEntity()` 메서드로 변환한다. `@Component`로 등록하여 Spring 빈으로 관리한다. **Entity의 `create()` 팩토리 메서드를 사용하여 변환한다. setter는 사용하지 않는다.**

```java
@Component
public class PropertyEntityMapper {

    // Domain → Entity (저장 시) — create() 팩토리 메서드 사용
    public PropertyJpaEntity toEntity(Property domain) {
        return PropertyJpaEntity.create(
            domain.id() != null ? domain.id().value() : null,
            domain.partnerId().value(),
            domain.brandId() != null ? domain.brandId().value() : null,
            domain.propertyTypeId().value(),
            domain.name().value(),
            domain.description() != null ? domain.description().value() : null,
            domain.location().address(),
            domain.location().latitude(),
            domain.location().longitude(),
            domain.location().neighborhood(),
            domain.location().region(),
            domain.status().name(),
            domain.promotionText() != null ? domain.promotionText().value() : null,
            domain.createdAt(),
            domain.updatedAt(),
            null  // deletedAt
        );
    }

    // Entity → Domain (조회 시) — reconstitute()로 검증 없이 복원
    public Property toDomain(PropertyJpaEntity entity) {
        return Property.reconstitute(
            PropertyId.of(entity.getId()),
            PartnerId.of(entity.getPartnerId()),
            entity.getBrandId() != null ? BrandId.of(entity.getBrandId()) : null,
            PropertyTypeId.of(entity.getPropertyTypeId()),
            PropertyName.of(entity.getName()),
            // ...
        );
    }
}
```

**setter 없이 create()만 사용**: Entity에 setter가 없으므로 Mapper는 반드시 `create()` 팩토리 메서드를 통해 Entity를 생성한다. 필드가 추가/변경되면 `create()` 시그니처가 변경되어 Mapper도 함께 수정해야 하는 **컴파일 타임 신호**가 된다.

**왜 reconstitute()로 복원하는가**: Domain 객체의 `forNew()`는 비즈니스 검증을 수행한다. DB에서 읽은 데이터는 이미 검증을 통과한 것이므로 `reconstitute()`로 검증 없이 복원해야 한다.

---

### PER-ADP-001: Adapter CQRS 분리 [BLOCKER]

CommandAdapter는 JpaRepository만 의존한다. QueryAdapter는 QueryDslRepository만 의존한다. 하나의 Adapter가 양쪽을 모두 의존하면 안 된다.

```java
// Command Adapter — JpaRepository만 의존
@Component
@RequiredArgsConstructor
public class PropertyCommandAdapter implements PropertyCommandPort {

    private final PropertyJpaRepository jpaRepository;

    @Override
    public Long persist(Property property) {
        PropertyJpaEntity entity = PropertyEntityMapper.toEntity(property);
        PropertyJpaEntity saved = jpaRepository.save(entity);
        return saved.getId();
    }

    @Override
    public void persistAll(List<Property> properties) {
        List<PropertyJpaEntity> entities = properties.stream()
            .map(PropertyEntityMapper::toEntity)
            .toList();
        jpaRepository.saveAll(entities);
    }
}

// Query Adapter — QueryDslRepository만 의존
@Component
@RequiredArgsConstructor
public class PropertyQueryAdapter implements PropertyQueryPort {

    private final PropertyQueryDslRepository queryDslRepository;

    @Override
    public Optional<Property> findById(PropertyId id) {
        return queryDslRepository.findById(id.value())
            .map(PropertyEntityMapper::toDomain);
    }

    @Override
    public SliceResult<Property> findByCondition(PropertySliceCriteria criteria) {
        return queryDslRepository.findByCondition(criteria);
    }
}
```

**왜 분리하는가**: Command는 JPA의 영속성 컨텍스트(1차 캐시, dirty checking)를 활용한다. Query는 이런 기능이 필요 없고, QueryDSL로 필요한 데이터만 정확히 가져오는 것이 효율적이다. 두 관심사를 섞으면 "이 Adapter가 지금 write 모드인지 read 모드인지" 알기 어렵다.

---

### PER-ADP-002: persist — merge 방식 [BLOCKER]

Domain ↔ Entity 분리 구조에서는 JPA dirty checking이 아닌 merge 방식을 사용한다.

```
흐름:
1. QueryAdapter에서 Entity 조회 → Domain 변환 (toDomain)
2. Application Service/Manager에서 Domain 상태 변경 (비즈니스 메서드 호출)
3. CommandAdapter에서 Domain → Entity 변환 (toEntity) → save() (merge)
```

**왜 dirty checking이 아닌가**: Domain과 Entity가 분리되어 있으므로, Application에서 Domain 객체의 상태를 변경해도 JPA 영속성 컨텍스트가 추적하는 Entity에는 반영되지 않는다. 따라서 Domain → Entity로 다시 변환하여 `save()`(내부적으로 merge)를 호출해야 한다. 이 방식이 명시적이고 예측 가능하다.

---

### PER-REP-001: CQRS Repository 분리 [BLOCKER]

JpaRepository는 `save`/`saveAll`만 사용한다. `@Query`, `findBy*`, `deleteBy*` 등 커스텀 메서드를 추가하지 않는다.

```java
// JpaRepository — save만
public interface PropertyJpaRepository extends JpaRepository<PropertyJpaEntity, Long> {
    // 커스텀 메서드 추가 금지
    // @Query 금지
    // findBy* 금지
    // deleteBy* 금지 — hard delete 불가
}

// QueryDslRepository — 모든 조회
@Repository
@RequiredArgsConstructor
public class PropertyQueryDslRepository {

    private final JPAQueryFactory queryFactory;

    public Optional<PropertyJpaEntity> findById(Long id) {
        PropertyJpaEntity result = queryFactory
            .selectFrom(propertyJpaEntity)
            .where(
                propertyJpaEntity.id.eq(id),
                propertyJpaEntity.deleted.isFalse()  // soft delete 필터
            )
            .fetchOne();
        return Optional.ofNullable(result);
    }

    public SliceResult<Property> findByCondition(PropertySliceCriteria criteria) {
        List<PropertyJpaEntity> results = queryFactory
            .selectFrom(propertyJpaEntity)
            .where(
                propertyJpaEntity.deleted.isFalse(),
                eqPropertyType(criteria.propertyType()),
                goeStarRating(criteria.minStarRating()),
                eqNeighborhood(criteria.neighborhood()),
                gtCursor(criteria.cursor())
            )
            .orderBy(propertyJpaEntity.id.asc())
            .limit(criteria.size() + 1)  // hasNext 판단용
            .fetch();

        // SliceResult 변환
        boolean hasNext = results.size() > criteria.size();
        List<Property> content = results.stream()
            .limit(criteria.size())
            .map(PropertyEntityMapper::toDomain)
            .toList();

        return new SliceResult<>(content, hasNext, 
            hasNext ? content.getLast().getId().value() : null);
    }
}
```

**왜**: CQRS 원칙에 따라 Command(JpaRepository save)와 Query(QueryDslRepository)를 완전 분리하여 각 경로를 독립적으로 최적화한다. JPQL이나 Native Query는 타입 안전성이 없고, 리팩토링 시 컴파일러가 잡아주지 않는다. 모든 QueryDslRepository의 조회에는 `deleted.isFalse()` 조건이 기본으로 포함되어야 한다.

---

### PER-CND-001: QueryDSL ConditionBuilder [MAJOR]

BooleanExpression 조건은 ConditionBuilder 유틸 메서드로 분리하여 null-safe 처리한다.

```java
public class PropertyConditionBuilder {

    public static BooleanExpression eqPropertyType(PropertyType type) {
        return type != null ? propertyJpaEntity.propertyTypeCode.eq(type.name()) : null;
    }

    public static BooleanExpression eqNeighborhood(String neighborhood) {
        return StringUtils.hasText(neighborhood) ? propertyJpaEntity.neighborhood.eq(neighborhood) : null;
    }

    public static BooleanExpression gtCursor(Long cursor) {
        return cursor != null ? propertyJpaEntity.id.gt(cursor) : null;
    }

    public static BooleanExpression goeStarRating(Integer minStar) {
        return minStar != null ? propertyJpaEntity.starRating.goe(minStar) : null;
    }
}
```

**왜**: QueryDSL의 `.where()`에 null을 전달하면 해당 조건이 무시되므로, null 반환으로 동적 조건을 깔끔하게 처리할 수 있다. 조건 로직을 재사용하고 테스트를 독립적으로 가능하게 한다.

---

### PER-FLY-001: Flyway 마이그레이션 [BLOCKER]

스키마 변경은 Flyway로만 관리한다. `ddl-auto`는 `validate`만 허용.

```
파일 네이밍: V{yyyyMMddHHmm}__{설명}.sql
예시:
V202604021000__create_property_table.sql
V202604021001__create_room_type_table.sql
V202604021002__create_rate_plan_table.sql
V202604021003__create_inventory_table.sql
V202604021004__create_reservation_table.sql
V202604021005__create_outbox_table.sql
```

```sql
-- V202604021000__create_property_table.sql
CREATE TABLE property (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    partner_id  BIGINT       NOT NULL,
    brand_id    BIGINT       NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT         NULL,
    address     VARCHAR(500) NOT NULL,
    latitude    DOUBLE       NOT NULL DEFAULT 0,
    longitude   DOUBLE       NOT NULL DEFAULT 0,
    neighborhood VARCHAR(100) NULL,
    region      VARCHAR(100) NULL,
    status      VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    deleted_at  TIMESTAMP(6) NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_property_partner (partner_id),
    INDEX idx_property_region_status (region, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V202604021005__create_outbox_table.sql
CREATE TABLE outbox (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id  BIGINT       NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       JSON         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    processed_at  TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**왜**: `ddl-auto=update`는 운영 환경에서 예기치 않은 ALTER TABLE을 실행하여 장시간 테이블 잠금을 유발할 수 있다. Flyway는 마이그레이션 이력을 추적하고, 롤백 시나리오를 명확히 한다.

---

### PER-CFG-001: JPA 설정 [BLOCKER]

```yaml
spring:
  jpa:
    open-in-view: false          # 필수 — OSIV 비활성화
    hibernate:
      ddl-auto: validate         # Flyway로 관리
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

**왜 OSIV false인가**: OSIV가 true이면 HTTP 요청 처리가 끝날 때까지 DB 커넥션을 점유한다. API 서버에서는 응답 직렬화 중에도 커넥션이 잡혀있어 풀 고갈로 이어진다. false로 설정하면 트랜잭션이 끝나는 시점에 커넥션이 반환된다.

---

## 네이밍 컨벤션 요약

| 파일명 패턴 | 용도 | 비고 |
|------------|------|------|
| `{Domain}JpaEntity.java` | JPA Entity | BaseAuditEntity 상속, Long FK |
| `{Domain}EntityMapper.java` | Domain ↔ Entity 변환 | static toDomain/toEntity |
| `{Domain}CommandAdapter.java` | CommandPort 구현 | JpaRepository만 의존 |
| `{Domain}QueryAdapter.java` | QueryPort 구현 | QueryDslRepository만 의존 |
| `{Domain}JpaRepository.java` | save/saveAll만 | 커스텀 메서드 금지 |
| `{Domain}QueryDslRepository.java` | 모든 조회 | soft delete 필터 포함 |

---

## 패키지 구조

```
adapter-out/persistence-mysql/
├── config/
│   └── JpaConfig.java                    ← JPA 설정, Auditing 활성화
│
├── entity/
│   ├── BaseAuditEntity.java              ← @MappedSuperclass
│   ├── SoftDeletableEntity.java
│   ├── PropertyJpaEntity.java
│   ├── RoomTypeJpaEntity.java
│   ├── RatePlanJpaEntity.java
│   ├── RateRuleJpaEntity.java
│   ├── RateJpaEntity.java
│   ├── InventoryJpaEntity.java
│   ├── ReservationJpaEntity.java
│   ├── ReservationItemJpaEntity.java
│   ├── PartnerJpaEntity.java
│   ├── PartnerMemberJpaEntity.java
│   ├── SupplierJpaEntity.java
│   ├── SupplierPropertyJpaEntity.java
│   ├── SupplierRoomTypeJpaEntity.java
│   ├── SupplierSyncLogJpaEntity.java
│   └── OutboxJpaEntity.java
│
├── mapper/
│   ├── PropertyEntityMapper.java
│   ├── RoomTypeEntityMapper.java
│   ├── RatePlanEntityMapper.java
│   ├── InventoryEntityMapper.java
│   ├── ReservationEntityMapper.java
│   ├── SupplierEntityMapper.java
│   └── OutboxEntityMapper.java
│
├── repository/
│   ├── jpa/                               ← Command 전용
│   │   ├── PropertyJpaRepository.java
│   │   ├── RoomTypeJpaRepository.java
│   │   ├── RateJpaRepository.java
│   │   ├── InventoryJpaRepository.java
│   │   ├── ReservationJpaRepository.java
│   │   ├── SupplierJpaRepository.java
│   │   └── OutboxJpaRepository.java
│   └── querydsl/                          ← Query 전용
│       ├── PropertyQueryDslRepository.java
│       ├── RoomTypeQueryDslRepository.java
│       ├── RateQueryDslRepository.java
│       ├── InventoryQueryDslRepository.java
│       ├── ReservationQueryDslRepository.java
│       ├── OutboxQueryDslRepository.java
│       └── condition/
│           ├── PropertyConditionBuilder.java
│           ├── InventoryConditionBuilder.java
│           └── ReservationConditionBuilder.java
│
├── adapter/
│   ├── command/                           ← CommandPort 구현
│   │   ├── PropertyCommandAdapter.java
│   │   ├── RoomTypeCommandAdapter.java
│   │   ├── RateCommandAdapter.java
│   │   ├── InventoryCommandAdapter.java
│   │   ├── ReservationCommandAdapter.java
│   │   ├── SupplierCommandAdapter.java
│   │   └── OutboxCommandAdapter.java
│   └── query/                             ← QueryPort 구현
│       ├── PropertyQueryAdapter.java
│       ├── RoomTypeQueryAdapter.java
│       ├── RateQueryAdapter.java
│       ├── InventoryQueryAdapter.java
│       ├── ReservationQueryAdapter.java
│       ├── PartnerQueryAdapter.java
│       └── OutboxQueryAdapter.java
│
└── migration/
    ├── V202604021000__create_property_table.sql
    ├── V202604021001__create_room_type_table.sql
    ├── V202604021005__create_outbox_table.sql
    └── ...
```

---

## ArchUnit 테스트로 강제할 규칙

| 규칙 | 검증 내용 |
|------|-----------|
| Entity 접미사 | persistence-mysql의 entity/ 패키지 클래스는 *JpaEntity 접미사 필수 |
| Entity 비즈니스 로직 금지 | Entity 클래스에 Domain 행위 메서드(rename, cancel 등) 존재 금지 |
| Entity Lombok 금지 | Entity 클래스에 @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor, @Builder, @Data 등 Lombok 어노테이션 사용 금지 |
| Entity 변경 메서드 금지 | Entity 클래스에 set*, update*, change*, delete* 메서드 금지 (create, get, is만 허용) |
| Entity 생성자 접근 제어 | Entity에 public 생성자 금지. protected 기본 생성자 + private 전체 필드 생성자만 허용 |
| CQRS Adapter 분리 | command/ Adapter는 JpaRepository만, query/ Adapter는 QueryDslRepository만 의존 |
| JPA 관계 어노테이션 금지 | @OneToMany, @ManyToOne, @OneToOne, @ManyToMany 사용 금지 (Aggregate 내부 포함) |
| JPQL/NativeQuery 금지 | @Query 어노테이션 사용 금지 |
| delete 메서드 금지 | JpaRepository에 deleteBy*, delete 커스텀 메서드 금지 |
| soft delete 필터 | QueryDslRepository의 조회 메서드에 deleted.isFalse() 조건 포함 필수 |
