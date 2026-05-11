package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.SubscriptionAttemptJpaEntity;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.AttemptId;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionAttemptEntityMapper {

    public SubscriptionAttemptJpaEntity toEntity(SubscriptionAttempt domain) {
        return SubscriptionAttemptJpaEntity.create(
            domain.id().value(),
            domain.memberId().value(),
            domain.channelId().value(),
            domain.kind(),
            domain.fromStatus(),
            domain.toStatus(),
            domain.requestedAt(),
            domain.completedAt(),
            domain.status(),
            domain.failureReason(),
            domain.failureDetail(),
            domain.idempotencyKey()
        );
    }

    public SubscriptionAttempt toDomain(SubscriptionAttemptJpaEntity entity) {
        return SubscriptionAttempt.reconstitute(
            AttemptId.of(entity.getId()),
            MemberId.of(entity.getMemberId()),
            ChannelId.of(entity.getChannelId()),
            entity.getKind(),
            entity.getFromStatus(),
            entity.getToStatus(),
            entity.getRequestedAt(),
            entity.getCompletedAt(),
            entity.getStatus(),
            entity.getFailureReason(),
            entity.getFailureDetail(),
            entity.getIdempotencyKey()
        );
    }
}
