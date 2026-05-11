package com.ryuqqq.alt.adapter.out.persistence.mysql.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.SubscriptionAttemptJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.SubscriptionAttemptEntityMapper;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptId;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionAttemptEntityMapper 단위 테스트 — Domain ↔ Entity round-trip")
class SubscriptionAttemptEntityMapperTest {

    private final SubscriptionAttemptEntityMapper mapper = new SubscriptionAttemptEntityMapper();

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("COMMITTED attempt 의 모든 필드가 Entity 로 옮겨진다 (failureReason / failureDetail 은 null)")
        void toEntity_committed() {
            SubscriptionAttempt attempt = SubscriptionAttempt.reconstitute(
                AttemptId.of(42L),
                MemberId.of(7L),
                ChannelId.of(1L),
                AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE,
                SubscriptionStatus.PREMIUM,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-10T00:00:01Z"),
                AttemptStatus.COMMITTED,
                null,
                null,
                "idem-key-001"
            );

            SubscriptionAttemptJpaEntity entity = mapper.toEntity(attempt);

            assertThat(entity.getId()).isEqualTo(42L);
            assertThat(entity.getMemberId()).isEqualTo(7L);
            assertThat(entity.getChannelId()).isEqualTo(1L);
            assertThat(entity.getKind()).isEqualTo(AttemptKind.SUBSCRIBE);
            assertThat(entity.getFromStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(entity.getToStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(entity.getStatus()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(entity.getFailureReason()).isNull();
            assertThat(entity.getFailureDetail()).isNull();
            assertThat(entity.getIdempotencyKey()).isEqualTo("idem-key-001");
        }

        @Test
        @DisplayName("FAILED attempt 의 failureReason / failureDetail 이 보존된다")
        void toEntity_failed_preservesReasonAndDetail() {
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedFailed(
                100L, AttemptFailureReason.EXTERNAL_TIMEOUT
            );

            SubscriptionAttemptJpaEntity entity = mapper.toEntity(attempt);

            assertThat(entity.getStatus()).isEqualTo(AttemptStatus.FAILED);
            assertThat(entity.getFailureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT);
            assertThat(entity.getFailureDetail()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_FAILURE_DETAIL);
        }
    }

    @Nested
    @DisplayName("round-trip — toEntity → toDomain")
    class RoundTrip {

        @Test
        @DisplayName("COMMITTED 시나리오 round-trip")
        void roundTrip_committed() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.reconstitutedCommitted(11L);

            SubscriptionAttempt roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertEquivalent(roundTripped, original);
        }

        @Test
        @DisplayName("ROLLED_BACK 시나리오 round-trip")
        void roundTrip_rolledBack() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.reconstitutedRolledBack(22L);

            SubscriptionAttempt roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertEquivalent(roundTripped, original);
            assertThat(roundTripped.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(roundTripped.failureDetail()).isNull();
        }

        @Test
        @DisplayName("FAILED 시나리오 round-trip — failureReason / failureDetail 보존")
        void roundTrip_failed() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.reconstitutedFailed(
                33L, AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN
            );

            SubscriptionAttempt roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertEquivalent(roundTripped, original);
            assertThat(roundTripped.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN);
            assertThat(roundTripped.failureDetail()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_FAILURE_DETAIL);
        }

        @Test
        @DisplayName("UNSUBSCRIBE 종류와 PREMIUM→NONE 전이 round-trip")
        void roundTrip_unsubscribe() {
            SubscriptionAttempt original = SubscriptionAttempt.reconstitute(
                AttemptId.of(44L),
                MemberId.of(99L),
                ChannelId.of(5L),
                AttemptKind.UNSUBSCRIBE,
                SubscriptionStatus.PREMIUM,
                SubscriptionStatus.NONE,
                Instant.parse("2026-05-09T08:00:00Z"),
                Instant.parse("2026-05-09T08:00:03Z"),
                AttemptStatus.COMMITTED,
                null, null,
                "unsubscribe-key"
            );

            SubscriptionAttempt roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertThat(roundTripped.kind()).isEqualTo(AttemptKind.UNSUBSCRIBE);
            assertThat(roundTripped.fromStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(roundTripped.toStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertEquivalent(roundTripped, original);
        }
    }

    private static void assertEquivalent(SubscriptionAttempt actual, SubscriptionAttempt expected) {
        assertThat(actual.idValue()).isEqualTo(expected.idValue());
        assertThat(actual.memberId().value()).isEqualTo(expected.memberId().value());
        assertThat(actual.channelId().value()).isEqualTo(expected.channelId().value());
        assertThat(actual.kind()).isEqualTo(expected.kind());
        assertThat(actual.fromStatus()).isEqualTo(expected.fromStatus());
        assertThat(actual.toStatus()).isEqualTo(expected.toStatus());
        assertThat(actual.requestedAt()).isEqualTo(expected.requestedAt());
        assertThat(actual.completedAt()).isEqualTo(expected.completedAt());
        assertThat(actual.status()).isEqualTo(expected.status());
        assertThat(actual.idempotencyKey()).isEqualTo(expected.idempotencyKey());
    }
}
