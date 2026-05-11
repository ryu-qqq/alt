package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * 구독/해지 시도(saga) Aggregate Root.
 *
 * 정책 (ADR-0006):
 * - 모든 attempt 는 생성 시점에 이미 종결 상태(COMMITTED / ROLLED_BACK / FAILED) 를 가진다.
 * - PENDING 중간 상태는 두지 않는다. 외부 응답을 받은 직후 결과 반영.
 * - 모든 필드 final, immutable.
 *
 * failureDetail: FAILED 상태일 때 어댑터에서 받은 메시지/컨텍스트를 박제 (운영 디버깅).
 *                COMMITTED / ROLLED_BACK 은 항상 null.
 *
 * idempotencyKey: 클라이언트 재시도 / 외부 retry 의 중복 처리 방지 (ADR-0004).
 */
public final class SubscriptionAttempt {

    private final AttemptId id;
    private final MemberId memberId;
    private final ChannelId channelId;
    private final AttemptKind kind;
    private final SubscriptionStatus fromStatus;
    private final SubscriptionStatus toStatus;
    private final Instant requestedAt;
    private final Instant completedAt;
    private final AttemptStatus status;
    private final AttemptFailureReason failureReason;
    private final String failureDetail;
    private final String idempotencyKey;

    private SubscriptionAttempt(
        AttemptId id,
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        Instant completedAt,
        AttemptStatus status,
        AttemptFailureReason failureReason,
        String failureDetail,
        String idempotencyKey
    ) {
        this.id = id;
        this.memberId = memberId;
        this.channelId = channelId;
        this.kind = kind;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
        this.status = status;
        this.failureReason = failureReason;
        this.failureDetail = failureDetail;
        this.idempotencyKey = idempotencyKey;
    }

    public static SubscriptionAttempt committed(
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        Instant completedAt,
        String idempotencyKey
    ) {
        return new SubscriptionAttempt(
            AttemptId.forNew(), memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            AttemptStatus.COMMITTED, null, null, idempotencyKey
        );
    }

    public static SubscriptionAttempt rolledBack(
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        Instant completedAt,
        String idempotencyKey
    ) {
        return new SubscriptionAttempt(
            AttemptId.forNew(), memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            AttemptStatus.ROLLED_BACK, AttemptFailureReason.EXTERNAL_REJECTED, null, idempotencyKey
        );
    }

    public static SubscriptionAttempt failed(
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        Instant completedAt,
        AttemptFailureReason reason,
        String failureDetail,
        String idempotencyKey
    ) {
        return new SubscriptionAttempt(
            AttemptId.forNew(), memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            AttemptStatus.FAILED, reason, failureDetail, idempotencyKey
        );
    }

    public static SubscriptionAttempt reconstitute(
        AttemptId id,
        MemberId memberId,
        ChannelId channelId,
        AttemptKind kind,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        Instant requestedAt,
        Instant completedAt,
        AttemptStatus status,
        AttemptFailureReason failureReason,
        String failureDetail,
        String idempotencyKey
    ) {
        return new SubscriptionAttempt(
            id, memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            status, failureReason, failureDetail, idempotencyKey
        );
    }

    public boolean isCommitted() {
        return status == AttemptStatus.COMMITTED;
    }

    /**
     * 영속화 직후 DB 가 채번한 memberId 를 attempt 에 주입하는 용도.
     * 다른 필드는 그대로 보존, memberId 만 갈아끼운 새 인스턴스 반환.
     */
    public SubscriptionAttempt withMemberId(MemberId newMemberId) {
        return new SubscriptionAttempt(
            id, newMemberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            status, failureReason, failureDetail, idempotencyKey
        );
    }

    /**
     * 영속화 직후 DB 가 채번한 attemptId 를 attempt 에 주입하는 용도.
     * 다른 필드는 그대로 보존, id 만 갈아끼운 새 인스턴스 반환.
     */
    public SubscriptionAttempt withId(AttemptId newId) {
        return new SubscriptionAttempt(
            newId, memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            status, failureReason, failureDetail, idempotencyKey
        );
    }

    /**
     * 외부 거절(random=0)로 인해 동일 시도를 ROLLED_BACK 으로 갈아끼운 새 인스턴스 반환.
     * 컨텍스트(memberId/channelId/timestamps/idempotencyKey 등)는 그대로 보존.
     */
    public SubscriptionAttempt asRolledBack() {
        return new SubscriptionAttempt(
            id, memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            AttemptStatus.ROLLED_BACK, AttemptFailureReason.EXTERNAL_REJECTED, null, idempotencyKey
        );
    }

    /**
     * 외부 호출 실패로 인해 동일 시도를 FAILED 로 갈아끼운 새 인스턴스 반환.
     * reason 과 detail 은 어댑터가 분류한 실패 컨텍스트를 그대로 박제.
     */
    public SubscriptionAttempt asFailed(AttemptFailureReason reason, String failureDetail) {
        return new SubscriptionAttempt(
            id, memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            AttemptStatus.FAILED, reason, failureDetail, idempotencyKey
        );
    }

    public AttemptId id() { return id; }
    public MemberId memberId() { return memberId; }
    public ChannelId channelId() { return channelId; }
    public AttemptKind kind() { return kind; }
    public SubscriptionStatus fromStatus() { return fromStatus; }
    public SubscriptionStatus toStatus() { return toStatus; }
    public Instant requestedAt() { return requestedAt; }
    public Instant completedAt() { return completedAt; }
    public AttemptStatus status() { return status; }
    public AttemptFailureReason failureReason() { return failureReason; }
    public String failureDetail() { return failureDetail; }
    public String idempotencyKey() { return idempotencyKey; }

    /** raw value accessors — 호출처 LoD(2단계 체이닝) 회피용. */
    public Long idValue() { return id.value(); }
    public Long channelIdValue() { return channelId.value(); }

    /** display name accessors — 호출처가 enum 표현 메서드를 직접 호출하지 않게 한다. */
    public String kindDisplayName() { return kind.displayName(); }
    public String fromStatusDisplayName() { return fromStatus.displayName(); }
    public String toStatusDisplayName() { return toStatus.displayName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubscriptionAttempt other)) return false;
        return id != null && !id.isNew() && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
