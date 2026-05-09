package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.error.InvalidTransitionException;
import com.ryuqqq.alt.domain.error.SubscriptionErrorCode;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionAttempt 사가 상태 머신")
class SubscriptionAttemptTest {

    private static final MemberId MEMBER = MemberId.of(1L);
    private static final ChannelId CHANNEL = ChannelId.of(10L);
    private static final Instant REQUESTED_AT = Instant.parse("2026-05-09T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-05-09T00:00:01Z");

    private SubscriptionAttempt newSubscribeAttempt() {
        return SubscriptionAttempt.forNew(
            MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            REQUESTED_AT
        );
    }

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("forNew는 PENDING 상태로 시작, terminal=false")
        void startsAsPending() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            assertThat(attempt.status()).isEqualTo(AttemptStatus.PENDING);
            assertThat(attempt.isTerminal()).isFalse();
            assertThat(attempt.failureReason()).isNull();
            assertThat(attempt.completedAt()).isNull();
            assertThat(attempt.id().isNew()).isTrue();
        }

        @Test
        @DisplayName("reconstitute는 종결 상태도 그대로 복원")
        void reconstituteTerminal() {
            SubscriptionAttempt attempt = SubscriptionAttempt.reconstitute(
                AttemptId.of(99L), MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, AttemptStatus.COMMITTED, null, COMPLETED_AT
            );
            assertThat(attempt.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(attempt.isTerminal()).isTrue();
        }
    }

    @Nested
    @DisplayName("T-2. 종결 전이 — PENDING → terminal")
    class TerminalTransition {

        @Test
        @DisplayName("commit: COMMITTED + completedAt 기록, failureReason 없음")
        void commit() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.commit(COMPLETED_AT);

            assertThat(attempt.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(attempt.completedAt()).isEqualTo(COMPLETED_AT);
            assertThat(attempt.failureReason()).isNull();
            assertThat(attempt.isTerminal()).isTrue();
        }

        @Test
        @DisplayName("rollback: ROLLED_BACK + reason=CSRNG_REJECTED")
        void rollback() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.rollback(COMPLETED_AT);

            assertThat(attempt.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(attempt.failureReason()).isEqualTo(AttemptFailureReason.CSRNG_REJECTED);
            assertThat(attempt.completedAt()).isEqualTo(COMPLETED_AT);
        }

        @Test
        @DisplayName("fail(CSRNG_UNAVAILABLE): FAILED + 사유 보존")
        void failUnavailable() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, COMPLETED_AT);

            assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(attempt.failureReason()).isEqualTo(AttemptFailureReason.CSRNG_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("T-3. 불변식 — terminal 상태에서 변경 불가")
    class TerminalInvariant {

        @Test
        @DisplayName("COMMITTED 후 다시 commit 시도하면 ATTEMPT_NOT_PENDING")
        void doubleCommit() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.commit(COMPLETED_AT);

            assertThatThrownBy(() -> attempt.commit(COMPLETED_AT))
                .isInstanceOf(InvalidTransitionException.class)
                .satisfies(e -> assertThat(((InvalidTransitionException) e).errorCode())
                    .isEqualTo(SubscriptionErrorCode.ATTEMPT_NOT_PENDING));
        }

        @Test
        @DisplayName("ROLLED_BACK 후 fail 시도하면 ATTEMPT_NOT_PENDING")
        void rolledBackThenFail() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.rollback(COMPLETED_AT);

            assertThatThrownBy(() -> attempt.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, COMPLETED_AT))
                .isInstanceOf(InvalidTransitionException.class);
        }

        @Test
        @DisplayName("FAILED 후 commit 시도하면 ATTEMPT_NOT_PENDING")
        void failedThenCommit() {
            SubscriptionAttempt attempt = newSubscribeAttempt();
            attempt.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, COMPLETED_AT);

            assertThatThrownBy(() -> attempt.commit(COMPLETED_AT))
                .isInstanceOf(InvalidTransitionException.class);
        }
    }
}
