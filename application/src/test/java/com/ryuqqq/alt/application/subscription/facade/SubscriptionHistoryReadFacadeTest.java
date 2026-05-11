package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.manager.HistorySummaryReadManager;
import com.ryuqqq.alt.application.subscription.manager.SubscriptionAttemptReadManager;
import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistoryFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionHistoryReadFacade — 이력 readOnly 트랜잭션 묶음 단위 테스트")
class SubscriptionHistoryReadFacadeTest {

    @Mock MemberQueryPort memberQueryPort;
    @Mock SubscriptionAttemptReadManager subscriptionAttemptReadManager;
    @Mock ChannelReadManager channelReadManager;
    @Mock HistorySummaryReadManager historySummaryReadManager;

    @InjectMocks SubscriptionHistoryReadFacade facade;

    private static final PhoneNumber PHONE = PhoneNumber.of("01012345678");

    @Nested
    @DisplayName("정상 흐름")
    class Success {

        @Test
        @DisplayName("Member → History → Channels → Summary 순서로 조회 후 Bundle 을 빌드한다")
        void shouldQueryAllAndBuildBundleInOrder() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();
            Channels channels = ChannelFixture.defaultChannels();
            HistorySummary summary = HistorySummary.of(member.id(), history.latestCommittedAttemptId(), "캐시된 요약");

            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.of(member));
            given(subscriptionAttemptReadManager.findHistoryByMemberId(member.id())).willReturn(history);
            given(channelReadManager.findByIds(history.committedChannelIds())).willReturn(channels);
            given(historySummaryReadManager.find(member.id())).willReturn(Optional.of(summary));

            // when
            SubscriptionHistoryReadBundle bundle = facade.findByPhoneNumber(PHONE);

            // then — 호출 순서 검증
            InOrder order = inOrder(memberQueryPort, subscriptionAttemptReadManager, channelReadManager, historySummaryReadManager);
            order.verify(memberQueryPort).findByPhoneNumber(PHONE);
            order.verify(subscriptionAttemptReadManager).findHistoryByMemberId(member.id());
            order.verify(channelReadManager).findByIds(history.committedChannelIds());
            order.verify(historySummaryReadManager).find(member.id());

            // bundle 조립 검증
            assertThat(bundle.member()).isSameAs(member);
            assertThat(bundle.history()).isSameAs(history);
            assertThat(bundle.channels()).isSameAs(channels);
            assertThat(bundle.persistedSummary()).isSameAs(summary);
            assertThat(bundle.fingerprint()).isEqualTo(history.latestCommittedAttemptId());
        }

        @Test
        @DisplayName("영속 Summary 가 없으면 bundle.persistedSummary 는 null 로 빌드된다")
        void shouldBuildBundleWithNullSummaryWhenAbsent() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            Channels channels = ChannelFixture.defaultChannels();

            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.of(member));
            given(subscriptionAttemptReadManager.findHistoryByMemberId(member.id())).willReturn(history);
            given(channelReadManager.findByIds(history.committedChannelIds())).willReturn(channels);
            given(historySummaryReadManager.find(member.id())).willReturn(Optional.empty());

            // when
            SubscriptionHistoryReadBundle bundle = facade.findByPhoneNumber(PHONE);

            // then
            assertThat(bundle.persistedSummary()).isNull();
            assertThat(bundle.hasMatchingSummary()).isFalse();
        }
    }

    @Nested
    @DisplayName("회원 없음")
    class MemberAbsent {

        @Test
        @DisplayName("phoneNumber 로 회원이 없으면 MemberNotFoundException 을 던지고 후속 manager 는 호출하지 않는다")
        void shouldThrowMemberNotFoundAndSkipDownstream() {
            // given
            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> facade.findByPhoneNumber(PHONE))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining(PHONE.value());

            verifyNoInteractions(subscriptionAttemptReadManager);
            verifyNoInteractions(channelReadManager);
            verifyNoInteractions(historySummaryReadManager);
        }
    }
}
