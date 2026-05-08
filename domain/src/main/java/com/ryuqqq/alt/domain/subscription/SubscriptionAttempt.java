package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 구독/해지 시도(saga) 한 건. PENDING으로 시작해 COMMITTED / ROLLED_BACK / FAILED 중 하나로 종결.
 * terminal 상태에서는 더 이상 변경 불가.
 */
public final class SubscriptionAttempt {

    private final AttemptId id;
    private final MemberId memberId;
    private final ChannelId channelId;
    private final AttemptKind kind;
    private final SubscriptionStatus fromStatus;
    private final SubscriptionStatus toStatus;
    private final Instant requestedAt;

    private AttemptStatus status;
    private AttemptFailureReason failureReason;
    private Instant completedAt;

    private SubscriptionAttempt(
        AttemptId id,
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        AttemptStatus status,
        AttemptFailureReason failureReason,
        Instant completedAt
    ) {
        this.id = id;
        this.memberId = memberId;
        this.channelId = channelId;
        this.kind = kind;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.requestedAt = requestedAt;
        this.status = status;
        this.failureReason = failureReason;
        this.completedAt = completedAt;
    }

    public static SubscriptionAttempt start(
        AttemptId id,
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt
    ) {
        return new SubscriptionAttempt(
            id, memberId, channelId, kind, fromStatus, toStatus,
            requestedAt, AttemptStatus.PENDING, null, null
        );
    }

    public static SubscriptionAttempt rehydrate(
        AttemptId id,
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        AttemptStatus status,
        AttemptFailureReason failureReason,
        Instant completedAt
    ) {
        return new SubscriptionAttempt(
            id, memberId, channelId, kind, fromStatus, toStatus,
            requestedAt, status, failureReason, completedAt
        );
    }

    public void commit(Instant at) {
        ensurePending();
        this.status = AttemptStatus.COMMITTED;
        this.completedAt = Objects.requireNonNull(at);
    }

    public void rollback(Instant at) {
        ensurePending();
        this.status = AttemptStatus.ROLLED_BACK;
        this.failureReason = AttemptFailureReason.CSRNG_REJECTED;
        this.completedAt = Objects.requireNonNull(at);
    }

    public void fail(AttemptFailureReason reason, Instant at) {
        ensurePending();
        this.status = AttemptStatus.FAILED;
        this.failureReason = Objects.requireNonNull(reason);
        this.completedAt = Objects.requireNonNull(at);
    }

    private void ensurePending() {
        if (status != AttemptStatus.PENDING) {
            throw new IllegalStateException(
                "attempt is not in PENDING state. current=" + status);
        }
    }

    public AttemptId id() { return id; }
    public MemberId memberId() { return memberId; }
    public ChannelId channelId() { return channelId; }
    public AttemptKind kind() { return kind; }
    public SubscriptionStatus fromStatus() { return fromStatus; }
    public SubscriptionStatus toStatus() { return toStatus; }
    public Instant requestedAt() { return requestedAt; }
    public AttemptStatus status() { return status; }
    public AttemptFailureReason failureReason() { return failureReason; }
    public Instant completedAt() { return completedAt; }
}
