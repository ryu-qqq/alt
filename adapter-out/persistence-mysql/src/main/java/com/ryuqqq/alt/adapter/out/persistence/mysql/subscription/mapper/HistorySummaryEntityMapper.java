package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.HistorySummaryJpaEntity;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;

@Component
public class HistorySummaryEntityMapper {

    public HistorySummaryJpaEntity toEntity(HistorySummary domain) {
        return HistorySummaryJpaEntity.create(
            domain.memberId().value(),
            domain.fingerprint(),
            domain.summary()
        );
    }

    public HistorySummary toDomain(HistorySummaryJpaEntity entity) {
        return HistorySummary.rehydrate(
            MemberId.of(entity.getMemberId()),
            entity.getFingerprint(),
            entity.getSummary(),
            entity.updatedAt()
        );
    }
}
