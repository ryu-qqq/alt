package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * SubscriptionAttempt 생성. TimeProvider 가 시간을 단일화한다 (APP-FAC-001).
 */
@Component
public class SubscriptionAttemptFactory {

    private final TimeProvider timeProvider;

    public SubscriptionAttemptFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public SubscriptionAttempt forSubscribe(
        MemberId memberId,
        ChannelId channelId,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        String idempotencyKey
    ) {
        Instant now = timeProvider.now();
        return SubscriptionAttempt.forNew(
            memberId, channelId, AttemptKind.SUBSCRIBE,
            fromStatus, toStatus, now, idempotencyKey
        );
    }

    public SubscriptionAttempt forUnsubscribe(
        MemberId memberId,
        ChannelId channelId,
        SubscriptionStatus fromStatus,
        SubscriptionStatus toStatus,
        String idempotencyKey
    ) {
        Instant now = timeProvider.now();
        return SubscriptionAttempt.forNew(
            memberId, channelId, AttemptKind.UNSUBSCRIBE,
            fromStatus, toStatus, now, idempotencyKey
        );
    }
}
