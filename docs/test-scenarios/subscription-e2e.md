# alt 구독 서비스 — E2E 통합 테스트 시나리오

> **대상**: bootstrap 모듈 `com.ryuqqq.alt.AltApplication` 진입점.
> **도메인**: 단일 BC — 구독 서비스 (회원 / 채널 / 구독 시도).
> **테스트 종류**: SpringBootTest + Testcontainers(MySQL 8) + Flyway + MockMvc + 외부 Port `@MockBean`.
>
> **참고 ADR**: 0001(헥사고날) / 0002(사가) / 0003(도메인 순수성) / 0004(멱등성)

---

## 1. 테스트 환경 (E2ETestBase)

| 컴포넌트 | 결정 |
|---------|------|
| Application 컨텍스트 | `@SpringBootTest(classes = AltApplication.class, webEnvironment = RANDOM_PORT)` |
| Profile | `e2e` (별도 `application-e2e.yml`) |
| MySQL | Testcontainers `MySQLContainer<>("mysql:8.0")` singleton (static + JVM 종료시 자동 정리) |
| Flyway | 자동 마이그레이션 (`spring.flyway.url` `@DynamicPropertySource` 주입) |
| MockMvc | `@AutoConfigureMockMvc` 로 통신 |
| 외부 API | `RandomClient`(csrng) / `LlmSummaryClient` 둘 다 `@MockitoBean` (WireMock 미도입 결정) |
| 데이터 격리 | 각 테스트 메서드 후 `@Sql(executionPhase=AFTER_TEST_METHOD)` 로 TRUNCATE — Flyway 시드(채널 6건) 유지 위해 member/subscription_attempt/history_summary 만 삭제 후 채널 재시드 |
| 캐시 격리 | `CaffeineIdempotencyRegistry` 빈을 직접 가져와 `cache.invalidateAll()` (@AfterEach) |
| Idempotency-Key | 매 테스트마다 `UUID.randomUUID()` (충돌 시나리오만 동일 키 재사용) |

### Idempotency 정정 (Adapter 코드 확인)

> 컨트롤러 docstring 과 `CaffeineIdempotencyRegistry` 구현 확인 결과 — **같은 Idempotency-Key 재호출은 첫 응답 재반환이 아니라 HTTP 409 거절** 이다.
> Caffeine 의 `get(key, mappingFunction)` 으로 첫 호출만 mappingFunction 을 실행하고, 두 번째 호출자는 holder 가 비어있어 `IdempotencyConflictException` → 409 변환.
> 따라서 시나리오 #7(reuse) 은 "두 번째 호출 409" 로 정정한다.

### LLM Adapter 활성화 조건

> `LlmClientAdapter` 는 `@ConditionalOnExpression("'${llm-client.api-key:}' != ''")` — 빈 환경에서는 `NoOpLlmClient` 가 대신 등록.
> 우리는 `LlmSummaryClient` Port 자체를 `@MockitoBean` 으로 교체 → 두 어댑터 모두 빈 등록 되지 않도록 `application-e2e.yml` 에 `llm-client.api-key=""` 유지.
> Mockito 가 Port 빈을 우선 주입한다.

---

## 2. 시나리오 인덱스 (총 17건)

| 번호 | 우선순위 | 카테고리 | 핵심 |
|------|---------|---------|------|
| S-01 | P0 | Subscribe 사가 | csrng=1 → COMMITTED + member.status 갱신 |
| S-02 | P0 | Subscribe 사가 | csrng=0 → ROLLED_BACK + member.status 불변 |
| S-03 | P0 | Subscribe 사가 | csrng 5xx → FAILED + EXTERNAL_SERVER_ERROR |
| S-04 | P0 | Subscribe 사가 | csrng timeout → FAILED + EXTERNAL_TIMEOUT |
| S-05 | P0 | Subscribe 사가 | 신규회원 + csrng 5xx → 회원 COMMIT, attempt FAILED |
| S-06a | P0 | Unsubscribe 사가 | csrng=1 → COMMITTED + 상태 강등 |
| S-06b | P0 | Unsubscribe 사가 | csrng=0 → ROLLED_BACK + 상태 불변 |
| S-06c | P0 | Unsubscribe 사가 | csrng 5xx → FAILED + EXTERNAL_SERVER_ERROR |
| S-07 | P0 | Idempotency | 같은 Idempotency-Key 두 번째 호출 → HTTP 409 |
| S-08 | P0 | Idempotency | 다른 Idempotency-Key → 둘 다 정상 처리 |
| S-09 | P1 | History + LLM | 이력 0건 → summary=null + LLM 호출 0회 |
| S-10 | P1 | History + LLM | 이력 N건 첫 조회 → LLM 호출 1회 + history_summary 영속 |
| S-11 | P1 | History + LLM | 같은 상태 재조회 → LLM 호출 0회 (캐시 hit) |
| S-12 | P1 | History + LLM | 새 COMMITTED 후 재조회 → LLM 재호출 1회 (fingerprint 변경) |
| S-13 | P1 | History + LLM | LLM unavailable → summary=null + history_summary 미저장 |
| S-14 | P1 | Validation | 휴대폰 형식 오류 → 400 + code=INVALID_PHONE_NUMBER |
| S-15 | P1 | Validation | 채널 미존재 → 404 + code=CHN-001 |
| S-16 | P1 | Validation | 정책 위반 (NONE → NONE) → registrationOnly 또는 SUB-001 |
| S-17 | P1 | Validation | (S-07 중복 — Idempotency 충돌이 곧 409) |

> S-17 은 S-07 과 같은 경로(409 IDEMPOTENCY_CONFLICT) — S-07 케이스로 통합.
> **유효 시나리오 16개** (S-01~S-16).

---

## 3. 시나리오 상세

### S-01: subscribe 해피패스 (csrng=1 → COMMITTED)

- **사전조건**: 신규 휴대폰번호 (DB 에 회원 없음), 채널 id=1 (홈페이지, BOTH).
- **요청**:
  ```http
  POST /api/v1/subscriptions/subscribe
  Idempotency-Key: <uuid>
  Content-Type: application/json
  { "phoneNumber": "01012345678", "channelId": 1, "targetStatus": "PREMIUM" }
  ```
- **외부 stub**: `RandomClient.call()` → `ExternalCallResult.APPROVED`
- **검증**:
  - HTTP 200
  - `data.status == "COMMITTED"`, `data.currentStatus == "PREMIUM"`, `data.failureReason == null`
  - DB `member.status == 'PREMIUM'`
  - DB `subscription_attempt.status == 'COMMITTED'` (kind=SUBSCRIBE, from=NONE, to=PREMIUM)
  - `RandomClient.call()` 호출 횟수 1

### S-02: subscribe 거절 (csrng=0 → ROLLED_BACK)

- **사전조건**: S-01 과 동일. 다른 휴대폰번호.
- **외부 stub**: `RandomClient.call()` → `ExternalCallResult.REJECTED`
- **검증**:
  - HTTP 200
  - `data.status == "ROLLED_BACK"`, `data.currentStatus == "NONE"`, `data.failureReason == "EXTERNAL_REJECTED"`
  - DB `member.status == 'NONE'` (신규 가입 직후 상태 유지)
  - DB attempt status=ROLLED_BACK, failure_reason=EXTERNAL_REJECTED

### S-03: subscribe 외부 5xx → FAILED

- **외부 stub**: `RandomClient.call()` → throw `new RandomClientException(EXTERNAL_SERVER_ERROR, "http 500")`
- **검증**:
  - HTTP 200 (사가는 5xx 를 application 결과로 흡수)
  - `data.status == "FAILED"`, `data.failureReason == "EXTERNAL_SERVER_ERROR"`
  - DB `member.status == 'NONE'` (member 등록은 됨)
  - DB attempt status=FAILED

### S-04: subscribe timeout → FAILED + EXTERNAL_TIMEOUT

- **외부 stub**: `RandomClient.call()` → throw `new RandomClientException(EXTERNAL_TIMEOUT, "read timeout")`
- **검증**:
  - HTTP 200, `data.status == "FAILED"`, `data.failureReason == "EXTERNAL_TIMEOUT"`

### S-05: 신규회원 등록 트랜잭션 분리 (member commit + attempt failed)

- **사전조건**: DB 에 회원 없음.
- **외부 stub**: csrng 5xx → RandomClientException
- **검증**:
  - HTTP 200, FAILED
  - DB `member` 1건 존재 (phone, status='NONE')
  - DB `subscription_attempt` 1건 (status=FAILED, member_id=가입한 member)
  - **member 트랜잭션 분리 확인** — attempt 가 FAILED 인데도 member 는 영속됨

### S-06a/b/c: unsubscribe 3분기

- **공통 사전조건**: `member.status == 'PREMIUM'` (직접 SQL 시드), 채널 id=5 (콜센터, UNSUBSCRIBE_ONLY).
- **요청 body**: `{ phoneNumber, channelId=5, targetStatus: "BASIC" }` (또는 NONE)
- **외부 stub 별 검증**:
  - APPROVED → `status="COMMITTED"`, `member.status='BASIC'`, attempt.kind=UNSUBSCRIBE
  - REJECTED → `status="ROLLED_BACK"`, `member.status='PREMIUM'`
  - 5xx → `status="FAILED"`, `member.status='PREMIUM'`

### S-07: 같은 Idempotency-Key 두 번째 호출 → HTTP 409

- **첫 호출**: 정상 (csrng APPROVED) — 200
- **두 번째 호출**: 같은 Key, 동일 body → HTTP 409 + code=`SUB-201`
- **검증**:
  - 두 번째 응답 `application/problem+json` content-type
  - `code == "SUB-201"` (IDEMPOTENCY_CONFLICT)
  - **두 번째 호출 시 `RandomClient.call()` 추가 호출되지 않음** (총 1회)
  - DB attempt 는 1건만 존재

### S-08: 다른 Idempotency-Key → 둘 다 처리

- 같은 회원, 같은 채널, 다른 Key → 두 번째도 정상 (단, 두 번째 시점에 member 가 이미 PREMIUM 이면 두 번째 요청은 정책 위반 가능 — targetStatus 를 PREMIUM 으로 같게 두면 BASIC→PREMIUM 전이라 도메인 정책상 OK)
- 두 번째 호출 시 stub APPROVED → 200 + COMMITTED
- DB attempt 2건, member 1건

### S-09: 이력 0건

- **사전조건**: 회원 없음 (또는 회원만 있고 COMMITTED 이력 없음).
- **요청**: `GET /api/v1/subscriptions/history?phoneNumber=01099999999`
- **검증**:
  - HTTP 200 또는 404 (회원 미존재 시) — `QuerySubscriptionHistoryService` 가 회원 없으면 빈 결과 반환하는지 확인 필요
  - `data.history == []`, `data.summary == null`
  - `LlmSummaryClient.summarize(...)` 호출 0회 (hasCommitted=false 이면 호출 안 함)

### S-10: 이력 N건 첫 조회

- **사전조건**: member 1건 + subscription_attempt COMMITTED 2건 SQL 직접 시드 (history_summary 없음).
- **stub**: `LlmSummaryClient.summarize(any)` → `LlmSummaryOutcome.success("최근 BASIC 구독 후 PREMIUM 으로 업그레이드")`
- **검증**:
  - HTTP 200
  - `data.history.size() == 2`, `data.summary` 가 stub 반환값과 일치
  - `LlmSummaryClient.summarize` 호출 1회
  - DB `history_summary` 1건 영속 (`member_id`, `fingerprint == 최신 COMMITTED attempt_id`, `summary` 일치)

### S-11: 같은 상태 재조회 (캐시 hit)

- **사전조건**: S-10 후 (history_summary 영속됨).
- **stub**: `LlmSummaryClient.summarize` (호출되지 않을 거지만 stub 설정 — `success("fresh")` — 실제 호출 시 검출되도록)
- **검증**:
  - HTTP 200
  - `data.summary == "최근 BASIC 구독 후 PREMIUM 으로 업그레이드"` (영속 그대로)
  - `LlmSummaryClient.summarize` 호출 횟수 = 0 (이 테스트 안에서)

### S-12: 새 COMMITTED 후 재조회 (fingerprint 변경 → LLM 재호출)

- **사전조건**: S-11 상태 + 추가 COMMITTED attempt 1건 직접 SQL insert (id 가 더 큰 값).
- **stub**: `LlmSummaryClient.summarize` → `success("new summary")`
- **검증**:
  - `data.summary == "new summary"`
  - LLM 호출 1회
  - `history_summary.fingerprint == 새로 추가된 attempt_id`
  - `history_summary.summary == "new summary"`

### S-13: LLM unavailable → summary=null + 미저장

- **사전조건**: member + COMMITTED attempt 1건 직접 시드 (history_summary 없음).
- **stub**: `LlmSummaryClient.summarize` → `LlmSummaryOutcome.unavailable("upstream 5xx")`
- **검증**:
  - HTTP 200, `data.summary == null`
  - `data.history.size() >= 1`
  - DB `history_summary` 미존재

### S-14: 휴대폰 번호 형식 오류

- **요청 body**: `{ phoneNumber: "abc", channelId: 1, targetStatus: "BASIC" }` + Idempotency-Key
- **검증**:
  - HTTP 400, content-type `application/problem+json`
  - `code == "MEM-002"` (INVALID_PHONE_NUMBER — PhoneNumber VO 정규식)
  - `RandomClient.call()` 호출 0회
- **주의**: `@NotBlank` 통과하지만 `PhoneNumber.of()` 가 validate 호출 → DomainException → 400.

### S-15: 채널 미존재 → 404

- **요청 body**: `{ phoneNumber: 정상, channelId: 99999, targetStatus: "BASIC" }`
- **검증**:
  - HTTP 404, `code == "CHN-001"`
  - `RandomClient.call()` 호출 0회

### S-16: 정책 위반 → registrationOnly 또는 SUB-001

- **요청 body**: 신규 회원, `targetStatus: "NONE"` (구독 시 target=NONE 은 registrationOnly 경로)
- **검증**:
  - 코드 분석: `SubscribeService.execute` 가 `isRegistrationOnly()` 면 `SubscribeResult.registrationOnly` 반환 → HTTP 200 + `currentStatus=NONE`, `status=null`, `attemptId=null`
  - 따라서 S-16 은 **NONE → NONE 가입 시 200 응답에 registrationOnly 결과** 검증
  - DB member 1건 (status=NONE), subscription_attempt 0건

---

## 4. 매니페스트

- **시나리오 수**: 16건
- **P0**: 9건 (S-01 ~ S-08)
- **P1**: 7건 (S-09 ~ S-16)
- **외부 stub 방식**: `RandomClient`, `LlmSummaryClient` Port 를 `@MockitoBean` 으로 교체 (WireMock 미도입)
- **DB 초기화 방식**: `@Sql` AFTER_TEST_METHOD 로 member/subscription_attempt/history_summary TRUNCATE + 채널은 Flyway 시드 유지
- **테스트 위치**: `bootstrap/src/test/java/com/ryuqqq/alt/e2e/`
- **테스트 클래스**:
  - `SubscribeE2ETest` — S-01 ~ S-05, S-08
  - `UnsubscribeE2ETest` — S-06a/b/c
  - `IdempotencyE2ETest` — S-07
  - `HistoryQueryE2ETest` — S-09 ~ S-13
  - `ValidationE2ETest` — S-14 ~ S-16

---

## 5. 위험 / 미해결

- **WireMock 미도입**: 본 PR 에서는 외부 Port `@MockitoBean` 으로 우회. 추후 실제 HTTP 레벨까지 검증하고 싶을 때 WireMock 도입.
- **bootstrap 테스트 의존성**: bootstrap/build.gradle.kts 에 testcontainers 의존성이 없음. dependency-guardian 위임이 필요한지 본 하네스 단계에서 추가 검토.
- **PhoneNumber 정규식**: 정확한 패턴은 도메인 코드에서 확인. 02/03 같은 일반전화는 거절될 가능성 — 형식 오류 케이스로는 영문자 "abc" 가 무난.
- **Member 트랜잭션 분리 (S-05)**: `MemberRegistrationCoordinator.findOrRegister` 가 `@Transactional(REQUIRES_NEW)` 인지 확인 필요. 분리 안 되어 있으면 5xx 시 member 도 롤백 — 시나리오 검증 결과 코드와 맞지 않으면 코드 의문점으로만 보고.
