package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity;

import com.ryuqqq.alt.adapter.out.persistence.mysql.common.BaseAuditEntity;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "subscription_attempt")
public class SubscriptionAttemptJpaEntity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private AttemptKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private SubscriptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private SubscriptionStatus toStatus;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttemptStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private AttemptFailureReason failureReason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "failure_detail", columnDefinition = "TEXT")
    private String failureDetail;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    protected SubscriptionAttemptJpaEntity() {
    }

    private SubscriptionAttemptJpaEntity(
        Long id, Long memberId, Long channelId, AttemptKind kind,
        SubscriptionStatus fromStatus, SubscriptionStatus toStatus,
        Instant requestedAt, Instant completedAt,
        AttemptStatus status, AttemptFailureReason failureReason,
        String failureDetail, String idempotencyKey
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

    public static SubscriptionAttemptJpaEntity create(
        Long id, Long memberId, Long channelId, AttemptKind kind,
        SubscriptionStatus fromStatus, SubscriptionStatus toStatus,
        Instant requestedAt, Instant completedAt,
        AttemptStatus status, AttemptFailureReason failureReason,
        String failureDetail, String idempotencyKey
    ) {
        return new SubscriptionAttemptJpaEntity(
            id, memberId, channelId, kind,
            fromStatus, toStatus, requestedAt, completedAt,
            status, failureReason, failureDetail, idempotencyKey
        );
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getChannelId() { return channelId; }
    public AttemptKind getKind() { return kind; }
    public SubscriptionStatus getFromStatus() { return fromStatus; }
    public SubscriptionStatus getToStatus() { return toStatus; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public AttemptStatus getStatus() { return status; }
    public AttemptFailureReason getFailureReason() { return failureReason; }
    public String getFailureDetail() { return failureDetail; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
