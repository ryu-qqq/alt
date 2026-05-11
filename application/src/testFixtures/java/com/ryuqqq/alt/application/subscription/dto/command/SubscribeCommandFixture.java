package com.ryuqqq.alt.application.subscription.dto.command;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

/**
 * SubscribeCommand 테스트용 Fixture.
 *
 * - DEFAULT_* 상수로 자주 쓰는 값을 통일.
 * - 시나리오별 정적 팩토리 제공 (NONE / BASIC / PREMIUM 타겟).
 */
public final class SubscribeCommandFixture {

    private SubscribeCommandFixture() {}

    public static final PhoneNumber DEFAULT_PHONE = PhoneNumber.of("01012345678");
    public static final ChannelId DEFAULT_CHANNEL_ID = ChannelId.of(13L);
    public static final String DEFAULT_IDEMPOTENCY_KEY = "fixture-subscribe-key-001";

    public static SubscribeCommand subscribePremium() {
        return SubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, SubscriptionStatus.PREMIUM, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static SubscribeCommand subscribeBasic() {
        return SubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, SubscriptionStatus.BASIC, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static SubscribeCommand registrationOnly() {
        return SubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, SubscriptionStatus.NONE, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static SubscribeCommand of(SubscriptionStatus targetStatus) {
        return SubscribeCommand.of(DEFAULT_PHONE, DEFAULT_CHANNEL_ID, targetStatus, DEFAULT_IDEMPOTENCY_KEY);
    }

    public static SubscribeCommand of(PhoneNumber phoneNumber, ChannelId channelId, SubscriptionStatus targetStatus, String idempotencyKey) {
        return SubscribeCommand.of(phoneNumber, channelId, targetStatus, idempotencyKey);
    }
}
