package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

import java.time.Instant;

/**
 * SubscribeBundle 테스트용 Fixture.
 *
 * 라이프사이클 단계별로 활용 가능한 번들을 제공한다 (factory 직후 / withMember 직후 / withChannel 직후).
 */
public final class SubscribeBundleFixture {

    private SubscribeBundleFixture() {}

    public static final Instant DEFAULT_NOW = Instant.parse("2026-05-10T00:00:00Z");
    public static final String DEFAULT_IDEMPOTENCY_KEY = "fixture-bundle-001";

    /**
     * factory 직후 모양: draft member (id=null) + channel=null + COMMITTED attempt.
     */
    public static SubscribeBundle initial(SubscriptionStatus targetStatus) {
        Member draftMember = MemberFixture.newMember();
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            draftMember.id(), ChannelFixture.bothChannel().id(), AttemptKind.SUBSCRIBE,
            draftMember.status(), targetStatus,
            DEFAULT_NOW, DEFAULT_NOW, DEFAULT_IDEMPOTENCY_KEY
        );
        return new SubscribeBundle(draftMember, null, attempt);
    }

    /**
     * withMember 직후 모양: persisted member + channel=null.
     */
    public static SubscribeBundle withPersistedMember(Member persistedMember, SubscriptionStatus targetStatus) {
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            persistedMember.id(), ChannelFixture.bothChannel().id(), AttemptKind.SUBSCRIBE,
            persistedMember.status(), targetStatus,
            DEFAULT_NOW, DEFAULT_NOW, DEFAULT_IDEMPOTENCY_KEY
        );
        return new SubscribeBundle(persistedMember, null, attempt);
    }

    /**
     * withChannel 직후 모양: persisted member + 주입된 channel.
     */
    public static SubscribeBundle ready(Member persistedMember, Channel channel, SubscriptionStatus targetStatus) {
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            persistedMember.id(), channel.id(), AttemptKind.SUBSCRIBE,
            persistedMember.status(), targetStatus,
            DEFAULT_NOW, DEFAULT_NOW, DEFAULT_IDEMPOTENCY_KEY
        );
        return new SubscribeBundle(persistedMember, channel, attempt);
    }
}
