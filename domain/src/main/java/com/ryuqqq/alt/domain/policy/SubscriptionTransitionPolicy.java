package com.ryuqqq.alt.domain.policy;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.error.ChannelNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidTransitionException;
import com.ryuqqq.alt.domain.error.SubscriptionErrorCode;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

/**
 * 구독/해지 시 채널 권한 + 상태 전이 규칙을 통합 검증.
 * 모든 검증은 객체의 1단계 메서드만 사용 (LoD 준수, 2단계 체이닝 금지).
 */
public final class SubscriptionTransitionPolicy {

    private SubscriptionTransitionPolicy() {}

    public static void verifySubscribe(Member member, Channel channel, SubscriptionStatus target) {
        if (!channel.canSubscribe()) {
            throw new ChannelNotAllowedException(
                SubscriptionErrorCode.CHANNEL_SUBSCRIBE_NOT_ALLOWED,
                "channelId=" + channel.idValue() + " type=" + channel.typeDisplayName());
        }
        if (!member.canSubscribeTo(target)) {
            throw new InvalidTransitionException(
                SubscriptionErrorCode.INVALID_SUBSCRIBE_TRANSITION,
                member.statusDisplayName() + " -> " + target.displayName());
        }
    }

    public static void verifyUnsubscribe(Member member, Channel channel, SubscriptionStatus target) {
        if (!channel.canUnsubscribe()) {
            throw new ChannelNotAllowedException(
                SubscriptionErrorCode.CHANNEL_UNSUBSCRIBE_NOT_ALLOWED,
                "channelId=" + channel.idValue() + " type=" + channel.typeDisplayName());
        }
        if (!member.canUnsubscribeTo(target)) {
            throw new InvalidTransitionException(
                SubscriptionErrorCode.INVALID_UNSUBSCRIBE_TRANSITION,
                member.statusDisplayName() + " -> " + target.displayName());
        }
    }
}
