package com.ryuqqq.alt.domain.policy;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.ChannelUnsubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;
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
            throw new ChannelSubscribeNotAllowedException(channel.toString());
        }
        if (!member.canSubscribeTo(target)) {
            throw new InvalidSubscribeTransitionException(
                member.statusDisplayName() + " -> " + target.displayName());
        }
    }

    public static void verifyUnsubscribe(Member member, Channel channel, SubscriptionStatus target) {
        if (!channel.canUnsubscribe()) {
            throw new ChannelUnsubscribeNotAllowedException(channel.toString());
        }
        if (!member.canUnsubscribeTo(target)) {
            throw new InvalidUnsubscribeTransitionException(
                member.statusDisplayName() + " -> " + target.displayName());
        }
    }
}
