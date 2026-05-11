package com.ryuqqq.alt.application.subscription.dto.command;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

/**
 * UnsubscribeCommand 테스트용 Fixture.
 */
public final class UnsubscribeCommandFixture {

    private UnsubscribeCommandFixture() {}

    public static final PhoneNumber DEFAULT_PHONE = PhoneNumber.of("01012345678");
    public static final ChannelId DEFAULT_CHANNEL_ID = ChannelId.of(13L);
    public static final String DEFAULT_IDEMPOTENCY_KEY = "fixture-unsubscribe-key-001";

    public static UnsubscribeCommand unsubscribeToNone() {
        return UnsubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, SubscriptionStatus.NONE, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static UnsubscribeCommand unsubscribeToBasic() {
        return UnsubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, SubscriptionStatus.BASIC, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static UnsubscribeCommand of(SubscriptionStatus targetStatus) {
        return UnsubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, targetStatus, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static UnsubscribeCommand of(PhoneNumber phoneNumber, ChannelId channelId, SubscriptionStatus targetStatus, String idempotencyKey) {
        return UnsubscribeCommand.of(phoneNumber, channelId, targetStatus, idempotencyKey);
    }
}
