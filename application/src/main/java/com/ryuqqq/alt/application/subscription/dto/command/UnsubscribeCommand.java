package com.ryuqqq.alt.application.subscription.dto.command;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

public record UnsubscribeCommand(
    PhoneNumber phoneNumber,
    ChannelId channelId,
    SubscriptionStatus targetStatus,
    String idempotencyKey
) {

    public static UnsubscribeCommand of(
        PhoneNumber phoneNumber,
        ChannelId channelId,
        SubscriptionStatus targetStatus,
        String idempotencyKey
    ) {
        return new UnsubscribeCommand(phoneNumber, channelId, targetStatus, idempotencyKey);
    }
}
