package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.SubscriptionAttemptCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.SubscriptionAttemptQueryAdapter;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionAttemptQueryAdapter 통합 테스트")
class SubscriptionAttemptQueryAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private SubscriptionAttemptQueryAdapter subscriptionAttemptQueryAdapter;

    @Autowired
    private SubscriptionAttemptCommandAdapter subscriptionAttemptCommandAdapter;

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    /** Flyway 시드 채널 (BOTH) */
    private static final ChannelId SEED_CHANNEL_ID = ChannelId.of(1L);

    private Long persistMember(String phone) {
        return memberCommandAdapter.persist(Member.forNew(PhoneNumber.of(phone), SubscriptionStatus.NONE));
    }

    @Nested
    @DisplayName("findByIdempotencyKey")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("존재하지 않는 멱등성 키면 Optional.empty 를 반환한다")
        void findByIdempotencyKey_nonExisting_returnsEmpty() {
            Optional<SubscriptionAttempt> result =
                subscriptionAttemptQueryAdapter.findByIdempotencyKey("never-saved-key");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("저장된 멱등성 키로 조회하면 attempt 를 반환한다")
        void findByIdempotencyKey_existing_returnsAttempt() {
            Long memberId = persistMember("01070000001");
            String idemKey = "idem-key-find-001";

            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MemberId.of(memberId), SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-10T00:00:01Z"),
                idemKey
            );
            subscriptionAttemptCommandAdapter.persist(attempt);

            Optional<SubscriptionAttempt> found =
                subscriptionAttemptQueryAdapter.findByIdempotencyKey(idemKey);

            assertThat(found).isPresent();
            assertThat(found.get().idempotencyKey()).isEqualTo(idemKey);
            assertThat(found.get().status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(found.get().memberId().value()).isEqualTo(memberId);
        }
    }

    @Nested
    @DisplayName("findAllByMemberId — requested_at DESC 정렬 보장 (이력 조회의 핵심 책임)")
    class FindAllByMemberIdSorted {

        @Test
        @DisplayName("회원 이력 4건이 모두 requested_at 내림차순으로 반환된다")
        void findAllByMemberId_sortedByRequestedAtDesc() {
            Long memberId = persistMember("01070000002");
            MemberId mid = MemberId.of(memberId);

            // 의도적으로 비순차적인 requested_at 으로 저장 (정렬 책임 검증)
            Instant t1 = Instant.parse("2026-05-10T10:00:00Z");
            Instant t2 = Instant.parse("2026-05-10T11:00:00Z");
            Instant t3 = Instant.parse("2026-05-10T12:00:00Z");
            Instant t4 = Instant.parse("2026-05-10T13:00:00Z");

            // 저장 순서: t2 → t4 → t1 → t3 (랜덤하게 섞음)
            subscriptionAttemptCommandAdapter.persist(committed(mid, t2, "k-002-a"));
            subscriptionAttemptCommandAdapter.persist(committed(mid, t4, "k-002-b"));
            subscriptionAttemptCommandAdapter.persist(committed(mid, t1, "k-002-c"));
            subscriptionAttemptCommandAdapter.persist(committed(mid, t3, "k-002-d"));

            List<SubscriptionAttempt> result = subscriptionAttemptQueryAdapter.findAllByMemberId(mid);

            assertThat(result).hasSize(4);
            assertThat(result).extracting(SubscriptionAttempt::requestedAt)
                .containsExactly(t4, t3, t2, t1); // DESC 정렬 보장
        }

        @Test
        @DisplayName("다른 회원의 이력은 포함되지 않는다")
        void findAllByMemberId_isolatesByMember() {
            Long aId = persistMember("01070000003");
            Long bId = persistMember("01070000004");

            subscriptionAttemptCommandAdapter.persist(committed(MemberId.of(aId), Instant.parse("2026-05-10T10:00:00Z"), "k-003-a"));
            subscriptionAttemptCommandAdapter.persist(committed(MemberId.of(bId), Instant.parse("2026-05-10T11:00:00Z"), "k-003-b"));
            subscriptionAttemptCommandAdapter.persist(committed(MemberId.of(aId), Instant.parse("2026-05-10T12:00:00Z"), "k-003-c"));

            List<SubscriptionAttempt> aHistory = subscriptionAttemptQueryAdapter.findAllByMemberId(MemberId.of(aId));
            List<SubscriptionAttempt> bHistory = subscriptionAttemptQueryAdapter.findAllByMemberId(MemberId.of(bId));

            assertThat(aHistory).hasSize(2);
            assertThat(aHistory).allMatch(a -> a.memberId().value().equals(aId));
            assertThat(bHistory).hasSize(1);
            assertThat(bHistory).allMatch(a -> a.memberId().value().equals(bId));
        }

        @Test
        @DisplayName("이력이 없는 회원은 빈 리스트를 반환한다")
        void findAllByMemberId_noHistory_returnsEmpty() {
            Long memberId = persistMember("01070000005");

            List<SubscriptionAttempt> result =
                subscriptionAttemptQueryAdapter.findAllByMemberId(MemberId.of(memberId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("FAILED / ROLLED_BACK 상태도 동일하게 포함되어 정렬된다")
        void findAllByMemberId_includesAllStatuses() {
            Long memberId = persistMember("01070000006");
            MemberId mid = MemberId.of(memberId);

            Instant t1 = Instant.parse("2026-05-10T10:00:00Z");
            Instant t2 = Instant.parse("2026-05-10T11:00:00Z");
            Instant t3 = Instant.parse("2026-05-10T12:00:00Z");

            subscriptionAttemptCommandAdapter.persist(failed(mid, t1, "k-006-a"));
            subscriptionAttemptCommandAdapter.persist(committed(mid, t3, "k-006-b"));
            subscriptionAttemptCommandAdapter.persist(rolledBack(mid, t2, "k-006-c"));

            List<SubscriptionAttempt> result = subscriptionAttemptQueryAdapter.findAllByMemberId(mid);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(SubscriptionAttempt::status)
                .containsExactly(AttemptStatus.COMMITTED, AttemptStatus.ROLLED_BACK, AttemptStatus.FAILED);
        }
    }

    // ===== helpers =====
    private static SubscriptionAttempt committed(MemberId memberId, Instant requestedAt, String idemKey) {
        return SubscriptionAttempt.committed(
            memberId, SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            requestedAt, requestedAt.plusSeconds(1), idemKey
        );
    }

    private static SubscriptionAttempt rolledBack(MemberId memberId, Instant requestedAt, String idemKey) {
        return SubscriptionAttempt.rolledBack(
            memberId, SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            requestedAt, requestedAt.plusSeconds(1), idemKey
        );
    }

    private static SubscriptionAttempt failed(MemberId memberId, Instant requestedAt, String idemKey) {
        return SubscriptionAttempt.failed(
            memberId, SEED_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            requestedAt, requestedAt.plusSeconds(1),
            AttemptFailureReason.EXTERNAL_TIMEOUT, "timeout after 2s", idemKey
        );
    }
}
