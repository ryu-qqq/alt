package com.ryuqqq.alt.domain.policy;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.error.ChannelNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidTransitionException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

/**
 * 구독/해지 시 채널 권한과 상태 전이 규칙을 통합 검증하는 정적 정책.
 * 어댑터/유스케이스가 우회해 직접 상태를 변경하지 못하도록 단일 진입점 역할.
 */
public final class SubscriptionTransitionPolicy {

    private SubscriptionTransitionPolicy() {}

    public static void verifySubscribe(Member member, Channel channel, SubscriptionStatus target) {
        if (!channel.type().canSubscribe()) {
            throw new ChannelNotAllowedException(
                "channel " + channel.id().value() + " (" + channel.type() + ") cannot subscribe");
        }
        if (!member.status().canSubscribeTo(target)) {
            throw new InvalidTransitionException(
                "subscribe transition forbidden: " + member.status() + " -> " + target);
        }
    }

    public static void verifyUnsubscribe(Member member, Channel channel, SubscriptionStatus target) {
        if (!channel.type().canUnsubscribe()) {
            throw new ChannelNotAllowedException(
                "channel " + channel.id().value() + " (" + channel.type() + ") cannot unsubscribe");
        }
        if (!member.status().canUnsubscribeTo(target)) {
            throw new InvalidTransitionException(
                "unsubscribe transition forbidden: " + member.status() + " -> " + target);
        }
    }
}
