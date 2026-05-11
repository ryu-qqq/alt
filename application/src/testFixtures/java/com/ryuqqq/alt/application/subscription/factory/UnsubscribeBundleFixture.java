package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

import java.time.Instant;

/**
 * UnsubscribeBundle 테스트용 Fixture.
 *
 * 해지는 신규 등록 경로가 없으므로 factory 직후부터 영속 member 가 들어 있다.
 */
public final class UnsubscribeBundleFixture {

    private UnsubscribeBundleFixture() {}

    public static final Instant DEFAULT_NOW = Instant.parse("2026-05-10T00:00:00Z");
    public static final String DEFAULT_IDEMPOTENCY_KEY = "fixture-unsubscribe-bundle-001";

    /**
     * factory 직후 모양: persisted member + channel=null + COMMITTED attempt (해지).
     */
    public static UnsubscribeBundle initial(Member member, SubscriptionStatus targetStatus) {
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            member.id(), ChannelFixture.bothChannel().id(), AttemptKind.UNSUBSCRIBE,
            member.status(), targetStatus,
            DEFAULT_NOW, DEFAULT_NOW, DEFAULT_IDEMPOTENCY_KEY
        );
        return new UnsubscribeBundle(member, null, attempt);
    }

    /**
     * withChannel 직후 모양: persisted member + 주입된 channel + 해지 attempt.
     */
    public static UnsubscribeBundle ready(Member member, Channel channel, SubscriptionStatus targetStatus) {
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            member.id(), channel.id(), AttemptKind.UNSUBSCRIBE,
            member.status(), targetStatus,
            DEFAULT_NOW, DEFAULT_NOW, DEFAULT_IDEMPOTENCY_KEY
        );
        return new UnsubscribeBundle(member, channel, attempt);
    }
}
