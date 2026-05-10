package com.ryuqqq.alt.application.subscription.dto.command;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

/**
 * 구독 요청 Command. 필드는 Domain VO 사용 (APP-DTO-001).
 * idempotencyKey 는 nullable — 헤더 누락도 허용 (점진적 도입, ADR-0004).
 */
public record SubscribeCommand(
    PhoneNumber phoneNumber,
    ChannelId channelId,
    SubscriptionStatus targetStatus,
    String idempotencyKey
) {

    public static SubscribeCommand of(
        PhoneNumber phoneNumber,
        ChannelId channelId,
        SubscriptionStatus targetStatus,
        String idempotencyKey
    ) {
        return new SubscribeCommand(phoneNumber, channelId, targetStatus, idempotencyKey);
    }
}
