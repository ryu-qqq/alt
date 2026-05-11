package com.ryuqqq.alt.application.subscription.dto;

import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistoryFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionHistoryReadBundle — 이력 조회 번들 단위 테스트")
class SubscriptionHistoryReadBundleTest {

    @Nested
    @DisplayName("of 정적 팩토리")
    class FactoryOf {

        @Test
        @DisplayName("history 의 committedAttempts 와 latestCommittedAttemptId 가 빌드 시점에 미리 계산된다")
        void shouldComputeCommittedAttemptsAndFingerprintAtBuildTime() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();
            Channels channels = ChannelFixture.defaultChannels();

            // when
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(member, history, channels, null);

            // then
            assertThat(bundle.committedAttempts()).isEqualTo(history.committedAttempts());
            assertThat(bundle.fingerprint()).isEqualTo(history.latestCommittedAttemptId());
        }

        @Test
        @DisplayName("빈 history 는 fingerprint=0L, committedAttempts=빈 리스트")
        void shouldHandleEmptyHistory() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory empty = SubscriptionHistoryFixture.empty();

            // when
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(member, empty, Channels.empty(), null);

            // then
            assertThat(bundle.fingerprint()).isEqualTo(0L);
            assertThat(bundle.committedAttempts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("LoD accessor 들 (memberId / phoneNumber)")
    class LoDAccessors {

        @Test
        @DisplayName("memberId() 는 member.id() 와 동일하다")
        void shouldExposeMemberId() {
            // given
            Member member = MemberFixture.reconstituted(42L, com.ryuqqq.alt.domain.member.SubscriptionStatus.NONE);
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                member, SubscriptionHistoryFixture.empty(), Channels.empty(), null
            );

            // when & then
            assertThat(bundle.memberId()).isEqualTo(member.id());
            assertThat(bundle.memberId().value()).isEqualTo(42L);
        }

        @Test
        @DisplayName("phoneNumber() 는 member.phoneNumber() 와 동일하다")
        void shouldExposePhoneNumber() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                member, SubscriptionHistoryFixture.empty(), Channels.empty(), null
            );

            // when & then
            assertThat(bundle.phoneNumber()).isEqualTo(member.phoneNumber());
        }
    }

    @Nested
    @DisplayName("hasCommitted")
    class HasCommitted {

        @Test
        @DisplayName("committedAttempts 가 비어 있으면 false")
        void shouldBeFalseWhenEmpty() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                MemberFixture.reconstitutedMember(),
                SubscriptionHistoryFixture.empty(),
                Channels.empty(),
                null
            );

            // when & then
            assertThat(bundle.hasCommitted()).isFalse();
        }

        @Test
        @DisplayName("committed attempt 가 있으면 true")
        void shouldBeTrueWhenHasCommitted() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                MemberFixture.reconstitutedMember(),
                SubscriptionHistoryFixture.singleCommitted(),
                ChannelFixture.defaultChannels(),
                null
            );

            // when & then
            assertThat(bundle.hasCommitted()).isTrue();
        }
    }

    @Nested
    @DisplayName("hasMatchingSummary")
    class HasMatchingSummary {

        @Test
        @DisplayName("persistedSummary 가 null 이면 false")
        void shouldBeFalseWhenNullSummary() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                MemberFixture.reconstitutedMember(),
                SubscriptionHistoryFixture.singleCommitted(),
                ChannelFixture.defaultChannels(),
                null
            );

            // when & then
            assertThat(bundle.hasMatchingSummary()).isFalse();
        }

        @Test
        @DisplayName("persistedSummary 의 fingerprint 가 일치하면 true")
        void shouldBeTrueWhenFingerprintMatches() {
            // given — singleCommitted 의 fingerprint = 1L
            Member member = MemberFixture.reconstitutedMember();
            HistorySummary persisted = HistorySummary.of(member.id(), 1L, "캐시 요약");
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                member,
                SubscriptionHistoryFixture.singleCommitted(),
                ChannelFixture.defaultChannels(),
                persisted
            );

            // when & then
            assertThat(bundle.hasMatchingSummary()).isTrue();
        }

        @Test
        @DisplayName("persistedSummary 의 fingerprint 가 다르면 false (stale)")
        void shouldBeFalseWhenFingerprintMismatch() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            HistorySummary stale = HistorySummary.of(member.id(), 999L, "오래된 요약");
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(
                member,
                SubscriptionHistoryFixture.singleCommitted(),
                ChannelFixture.defaultChannels(),
                stale
            );

            // when & then
            assertThat(bundle.hasMatchingSummary()).isFalse();
        }
    }
}
