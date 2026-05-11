package com.ryuqqq.alt.application.subscription.dto;

import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistoryFixture;

/**
 * SubscriptionHistoryReadBundle 테스트용 Fixture.
 *
 * 도메인 fixture (Member / SubscriptionHistory / Channels / HistorySummary) 를 조합하여
 * Bundle 을 빌드한다.
 */
public final class SubscriptionHistoryReadBundleFixture {

    private SubscriptionHistoryReadBundleFixture() {}

    public static SubscriptionHistoryReadBundle empty() {
        Member member = MemberFixture.reconstitutedMember();
        return SubscriptionHistoryReadBundle.of(
            member, SubscriptionHistoryFixture.empty(), Channels.empty(), null
        );
    }

    public static SubscriptionHistoryReadBundle singleCommittedNoSummary() {
        Member member = MemberFixture.reconstitutedMember();
        return SubscriptionHistoryReadBundle.of(
            member, SubscriptionHistoryFixture.singleCommitted(), ChannelFixture.defaultChannels(), null
        );
    }

    public static SubscriptionHistoryReadBundle singleCommittedWithMatchingSummary(String summary) {
        Member member = MemberFixture.reconstitutedMember();
        SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
        // singleCommitted 의 latestCommittedAttemptId = 1L → fingerprint 일치
        HistorySummary matched = HistorySummary.of(member.id(), 1L, summary);
        return SubscriptionHistoryReadBundle.of(member, history, ChannelFixture.defaultChannels(), matched);
    }

    public static SubscriptionHistoryReadBundle mixedCommittedNoSummary() {
        Member member = MemberFixture.reconstitutedMember();
        return SubscriptionHistoryReadBundle.of(
            member, SubscriptionHistoryFixture.mixedStatuses(), ChannelFixture.defaultChannels(), null
        );
    }

    public static SubscriptionHistoryReadBundle of(Member member, SubscriptionHistory history, Channels channels, HistorySummary persistedSummary) {
        return SubscriptionHistoryReadBundle.of(member, history, channels, persistedSummary);
    }
}
