package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity;

import com.ryuqqq.alt.adapter.out.persistence.mysql.common.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "history_summary")
public class HistorySummaryJpaEntity extends BaseAuditEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "fingerprint", nullable = false)
    private long fingerprint;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    protected HistorySummaryJpaEntity() {
    }

    private HistorySummaryJpaEntity(Long memberId, long fingerprint, String summary) {
        this.memberId = memberId;
        this.fingerprint = fingerprint;
        this.summary = summary;
    }

    public static HistorySummaryJpaEntity create(Long memberId, long fingerprint, String summary) {
        return new HistorySummaryJpaEntity(memberId, fingerprint, summary);
    }

    public Long getMemberId() {
        return memberId;
    }

    public long getFingerprint() {
        return fingerprint;
    }

    public String getSummary() {
        return summary;
    }
}
