package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

import java.time.Instant;

/**
 * SubscriptionAttempt BC 테스트용 Fixture.
 *
 * - 모든 attempt 는 종결 상태(COMMITTED / ROLLED_BACK / FAILED)로 생성된다 (ADR-0006).
 * - DEFAULT_* 상수는 가장 흔한 시나리오(NONE → PREMIUM 구독, 1초 처리 시간)를 가정한다.
 */
public final class SubscriptionAttemptFixture {

    private SubscriptionAttemptFixture() {}

    public static final MemberId DEFAULT_MEMBER_ID = MemberId.of(1L);
    public static final ChannelId DEFAULT_CHANNEL_ID = ChannelId.of(10L);
    public static final Instant DEFAULT_REQUESTED_AT = Instant.parse("2026-05-10T00:00:00Z");
    public static final Instant DEFAULT_COMPLETED_AT = Instant.parse("2026-05-10T00:00:01Z");
    public static final String DEFAULT_IDEMPOTENCY_KEY = "fixture-key-001";
    public static final String DEFAULT_FAILURE_DETAIL = "HTTP 503 Service Unavailable";

    public static SubscriptionAttempt committedSubscribe() {
        return SubscriptionAttempt.committed(
            DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt committedUnsubscribe() {
        return SubscriptionAttempt.committed(
            DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.UNSUBSCRIBE,
            SubscriptionStatus.PREMIUM, SubscriptionStatus.NONE,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt rolledBackSubscribe() {
        return SubscriptionAttempt.rolledBack(
            DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt failedSubscribe(AttemptFailureReason reason) {
        return SubscriptionAttempt.failed(
            DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT,
            reason, DEFAULT_FAILURE_DETAIL, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt reconstitutedCommitted(long id) {
        return SubscriptionAttempt.reconstitute(
            AttemptId.of(id), DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT,
            AttemptStatus.COMMITTED, null, null, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt reconstitutedCommitted(long id, ChannelId channelId) {
        return SubscriptionAttempt.reconstitute(
            AttemptId.of(id), DEFAULT_MEMBER_ID, channelId, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT,
            AttemptStatus.COMMITTED, null, null, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt reconstitutedRolledBack(long id) {
        return SubscriptionAttempt.reconstitute(
            AttemptId.of(id), DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT,
            AttemptStatus.ROLLED_BACK, AttemptFailureReason.EXTERNAL_REJECTED, null, DEFAULT_IDEMPOTENCY_KEY
        );
    }

    public static SubscriptionAttempt reconstitutedFailed(long id, AttemptFailureReason reason) {
        return SubscriptionAttempt.reconstitute(
            AttemptId.of(id), DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT,
            AttemptStatus.FAILED, reason, DEFAULT_FAILURE_DETAIL, DEFAULT_IDEMPOTENCY_KEY
        );
    }
}
