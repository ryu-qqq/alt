package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionAttempt 종결 팩토리")
class SubscriptionAttemptTest {

    private static final MemberId MEMBER = MemberId.of(1L);
    private static final ChannelId CHANNEL = ChannelId.of(10L);
    private static final Instant REQUESTED_AT = Instant.parse("2026-05-10T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-05-10T00:00:01Z");
    private static final String IDEMPOTENCY_KEY = "test-key-123";
    private static final String FAILURE_DETAIL = "HTTP 503 Service Unavailable";

    @Nested
    @DisplayName("T-1. 생성 — 종결 상태 직행")
    class TerminalCreation {

        @Test
        @DisplayName("committed: COMMITTED 상태, failureReason/failureDetail 없음")
        void committed() {
            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, IDEMPOTENCY_KEY
            );

            assertThat(attempt.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(attempt.failureReason()).isNull();
            assertThat(attempt.failureDetail()).isNull();
            assertThat(attempt.completedAt()).isEqualTo(COMPLETED_AT);
            assertThat(attempt.isCommitted()).isTrue();
            assertThat(attempt.id().isNew()).isTrue();
            assertThat(attempt.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("rolledBack: ROLLED_BACK 상태, EXTERNAL_REJECTED 사유 자동, detail 없음")
        void rolledBack() {
            SubscriptionAttempt attempt = SubscriptionAttempt.rolledBack(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, IDEMPOTENCY_KEY
            );

            assertThat(attempt.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(attempt.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(attempt.failureDetail()).isNull();
            assertThat(attempt.isCommitted()).isFalse();
        }

        @Test
        @DisplayName("failed: FAILED 상태 + 명시 사유 + detail 보존")
        void failed() {
            SubscriptionAttempt attempt = SubscriptionAttempt.failed(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT,
                AttemptFailureReason.EXTERNAL_TIMEOUT,
                FAILURE_DETAIL,
                IDEMPOTENCY_KEY
            );

            assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(attempt.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT);
            assertThat(attempt.failureDetail()).isEqualTo(FAILURE_DETAIL);
        }

        @Test
        @DisplayName("idempotencyKey 없이도 생성 가능 (선택적)")
        void idempotencyKeyOptional() {
            SubscriptionAttempt attempt = SubscriptionAttempt.committed(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.BASIC,
                REQUESTED_AT, COMPLETED_AT, null
            );
            assertThat(attempt.idempotencyKey()).isNull();
        }
    }

    @Nested
    @DisplayName("T-1. reconstitute — DB 복원")
    class Reconstitute {

        @Test
        @DisplayName("모든 필드를 그대로 복원")
        void reconstituteAllFields() {
            SubscriptionAttempt attempt = SubscriptionAttempt.reconstitute(
                AttemptId.of(99L), MEMBER, CHANNEL, AttemptKind.UNSUBSCRIBE,
                SubscriptionStatus.PREMIUM, SubscriptionStatus.NONE,
                REQUESTED_AT, COMPLETED_AT,
                AttemptStatus.FAILED, AttemptFailureReason.EXTERNAL_SERVER_ERROR,
                "HTTP 500", IDEMPOTENCY_KEY
            );

            assertThat(attempt.id().value()).isEqualTo(99L);
            assertThat(attempt.id().isNew()).isFalse();
            assertThat(attempt.kind()).isEqualTo(AttemptKind.UNSUBSCRIBE);
            assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(attempt.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_SERVER_ERROR);
            assertThat(attempt.failureDetail()).isEqualTo("HTTP 500");
        }
    }

    @Nested
    @DisplayName("T-6. 동등성")
    class Equality {

        @Test
        @DisplayName("같은 영속 ID 면 equals=true")
        void sameId() {
            SubscriptionAttempt a = SubscriptionAttempt.reconstitute(
                AttemptId.of(1L), MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, AttemptStatus.COMMITTED, null, null, IDEMPOTENCY_KEY
            );
            SubscriptionAttempt b = SubscriptionAttempt.reconstitute(
                AttemptId.of(1L), MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, AttemptStatus.COMMITTED, null, null, "other-key"
            );
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("forNew 끼리는 ID 가 isNew 라 equals=false")
        void forNewNotEqual() {
            SubscriptionAttempt a = SubscriptionAttempt.committed(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, null
            );
            SubscriptionAttempt b = SubscriptionAttempt.committed(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                REQUESTED_AT, COMPLETED_AT, null
            );
            assertThat(a).isNotEqualTo(b);
        }
    }
}
