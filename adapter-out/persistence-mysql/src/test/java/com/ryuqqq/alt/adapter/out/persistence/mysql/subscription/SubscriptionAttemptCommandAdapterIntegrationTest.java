package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.SubscriptionAttemptCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.SubscriptionAttemptQueryAdapter;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionAttemptCommandAdapter 통합 테스트")
class SubscriptionAttemptCommandAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private SubscriptionAttemptCommandAdapter subscriptionAttemptCommandAdapter;

    @Autowired
    private SubscriptionAttemptQueryAdapter subscriptionAttemptQueryAdapter;

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    @PersistenceContext
    private EntityManager entityManager;

    /** Flyway 시드 채널 (BOTH 타입) */
    private static final ChannelId SEED_CHANNEL_ID = ChannelId.of(1L);

    private Long persistMember(String phone) {
        return memberCommandAdapter.persist(Member.forNew(PhoneNumber.of(phone), SubscriptionStatus.NONE));
    }

    @Nested
    @DisplayName("종결 상태별 persist")
    class PersistTerminalStatuses {

        @Test
        @DisplayName("COMMITTED attempt 를 persist 하면 ID 가 채번되고 모든 필드가 보존된다")
        void persist_committed_preservesAllFields() {
            Long memberId = persistMember("01071000001");
            Instant requestedAt = Instant.parse("2026-05-10T01:00:00Z");
            Instant completedAt = Instant.parse("2026-05-10T01:00:01Z");
            String idemKey = "cmd-key-committed-001";

            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                requestedAt, completedAt, idemKey
            );

            Long attemptId = subscriptionAttemptCommandAdapter.persist(attempt);
            entityManager.flush();
            entityManager.clear();

            assertThat(attemptId).isPositive();

            SubscriptionAttempt saved = subscriptionAttemptQueryAdapter.findByIdempotencyKey(idemKey).orElseThrow();
            assertThat(saved.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(saved.kind()).isEqualTo(AttemptKind.SUBSCRIBE);
            assertThat(saved.fromStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(saved.toStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(saved.requestedAt()).isEqualTo(requestedAt);
            assertThat(saved.completedAt()).isEqualTo(completedAt);
            assertThat(saved.failureReason()).isNull();
            assertThat(saved.failureDetail()).isNull();
        }

        @Test
        @DisplayName("ROLLED_BACK attempt 를 persist 하면 failureReason=EXTERNAL_REJECTED 가 저장된다")
        void persist_rolledBack_preservesReason() {
            Long memberId = persistMember("01071000002");
            String idemKey = "cmd-key-rolled-002";

            SubscriptionAttempt attempt = SubscriptionAttempt.rolledBack(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T02:00:00Z"),
                Instant.parse("2026-05-10T02:00:01Z"),
                idemKey
            );

            subscriptionAttemptCommandAdapter.persist(attempt);
            entityManager.flush();
            entityManager.clear();

            SubscriptionAttempt saved = subscriptionAttemptQueryAdapter.findByIdempotencyKey(idemKey).orElseThrow();
            assertThat(saved.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(saved.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(saved.failureDetail()).isNull();
        }

        @Test
        @DisplayName("FAILED attempt 를 persist 하면 failureReason / failureDetail 이 보존된다")
        void persist_failed_preservesReasonAndDetail() {
            Long memberId = persistMember("01071000003");
            String idemKey = "cmd-key-failed-003";
            String detail = "HTTP 503 Service Unavailable";

            SubscriptionAttempt attempt = SubscriptionAttempt.failed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T03:00:00Z"),
                Instant.parse("2026-05-10T03:00:02Z"),
                AttemptFailureReason.EXTERNAL_SERVER_ERROR, detail, idemKey
            );

            subscriptionAttemptCommandAdapter.persist(attempt);
            entityManager.flush();
            entityManager.clear();

            SubscriptionAttempt saved = subscriptionAttemptQueryAdapter.findByIdempotencyKey(idemKey).orElseThrow();
            assertThat(saved.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(saved.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_SERVER_ERROR);
            assertThat(saved.failureDetail()).isEqualTo(detail);
        }
    }

    @Nested
    @DisplayName("idempotency_key UNIQUE 제약")
    class IdempotencyConflict {

        @Test
        @DisplayName("동일 idempotency_key 로 두 번 persist 하면 IdempotencyConflictException 으로 변환되어 던져진다")
        void persist_duplicateIdempotencyKey_throwsDomainException() {
            Long memberId = persistMember("01071000004");
            String idemKey = "cmd-key-dup-004";

            SubscriptionAttempt first = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T04:00:00Z"),
                Instant.parse("2026-05-10T04:00:01Z"),
                idemKey
            );
            subscriptionAttemptCommandAdapter.persist(first);
            entityManager.flush();

            SubscriptionAttempt duplicate = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T05:00:00Z"),
                Instant.parse("2026-05-10T05:00:01Z"),
                idemKey
            );

            assertThatThrownBy(() -> {
                subscriptionAttemptCommandAdapter.persist(duplicate);
                entityManager.flush();
            }).isInstanceOf(IdempotencyConflictException.class)
              .hasMessageContaining(idemKey);
        }
    }

    @Nested
    @DisplayName("idempotency_key NULL 허용")
    class NullIdempotencyKey {

        @Test
        @DisplayName("idempotency_key = null 인 attempt 도 persist 할 수 있다 (UNIQUE 제약 회피)")
        void persist_nullIdempotencyKey_isAllowed() {
            Long memberId = persistMember("01071000005");

            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T06:00:00Z"),
                Instant.parse("2026-05-10T06:00:01Z"),
                null
            );

            Long id = subscriptionAttemptCommandAdapter.persist(attempt);
            assertThat(id).isPositive();
        }

        @Test
        @DisplayName("idempotency_key = null 인 attempt 두 건은 UNIQUE 제약에 걸리지 않는다 (MySQL UNIQUE allows multiple NULL)")
        void persist_multipleNullIdempotencyKeys_doNotConflict() {
            Long memberId = persistMember("01071000006");

            SubscriptionAttempt a = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T07:00:00Z"),
                Instant.parse("2026-05-10T07:00:01Z"),
                null
            );
            SubscriptionAttempt b = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T08:00:00Z"),
                Instant.parse("2026-05-10T08:00:01Z"),
                null
            );

            Long idA = subscriptionAttemptCommandAdapter.persist(a);
            Long idB = subscriptionAttemptCommandAdapter.persist(b);
            entityManager.flush();

            assertThat(idA).isNotEqualTo(idB);
        }
    }

    @Nested
    @DisplayName("persist 후 즉시 query 가능")
    class RoundTrip {

        @Test
        @DisplayName("persist 한 attempt 가 findByIdempotencyKey 로 즉시 조회된다")
        void persist_thenQueryByIdempotencyKey_works() {
            Long memberId = persistMember("01071000007");
            String idemKey = "round-trip-007";

            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T09:00:00Z"),
                Instant.parse("2026-05-10T09:00:01Z"),
                idemKey
            );
            subscriptionAttemptCommandAdapter.persist(attempt);
            entityManager.flush();
            entityManager.clear();

            Optional<SubscriptionAttempt> found =
                subscriptionAttemptQueryAdapter.findByIdempotencyKey(idemKey);

            assertThat(found).isPresent();
            assertThat(found.get().idValue()).isPositive();
        }
    }
}
