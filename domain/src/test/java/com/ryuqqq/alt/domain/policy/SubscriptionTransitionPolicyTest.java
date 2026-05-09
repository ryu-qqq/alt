package com.ryuqqq.alt.domain.policy;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.channel.ChannelType;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.ChannelUnsubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;
import com.ryuqqq.alt.domain.error.SubscriptionErrorCode;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionTransitionPolicy — 채널 권한 + 상태 전이 통합 검증")
class SubscriptionTransitionPolicyTest {

    private static final PhoneNumber PHONE = PhoneNumber.of("01012345678");

    private Member memberWith(SubscriptionStatus status) {
        return Member.reconstitute(MemberId.of(1L), PHONE, status);
    }

    private Channel channelWith(ChannelType type) {
        return Channel.reconstitute(ChannelId.of(10L), "test-channel", type);
    }

    @Nested
    @DisplayName("T-4. 구독 검증")
    class VerifySubscribe {

        @Test
        @DisplayName("BOTH 채널 + 허용 전이 → 통과")
        void allowed() {
            Member member = memberWith(SubscriptionStatus.NONE);
            Channel channel = channelWith(ChannelType.BOTH);

            assertThatCode(() ->
                SubscriptionTransitionPolicy.verifySubscribe(member, channel, SubscriptionStatus.PREMIUM))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("UNSUBSCRIBE_ONLY 채널은 ChannelSubscribeNotAllowedException")
        void channelDenied() {
            Member member = memberWith(SubscriptionStatus.NONE);
            Channel channel = channelWith(ChannelType.UNSUBSCRIBE_ONLY);

            assertThatThrownBy(() ->
                SubscriptionTransitionPolicy.verifySubscribe(member, channel, SubscriptionStatus.BASIC))
                .isInstanceOf(ChannelSubscribeNotAllowedException.class)
                .satisfies(e -> assertThat(((ChannelSubscribeNotAllowedException) e).errorCode())
                    .isEqualTo(SubscriptionErrorCode.CHANNEL_SUBSCRIBE_NOT_ALLOWED));
        }

        @Test
        @DisplayName("PREMIUM 회원은 추가 구독 불가 → InvalidSubscribeTransitionException")
        void transitionDenied() {
            Member member = memberWith(SubscriptionStatus.PREMIUM);
            Channel channel = channelWith(ChannelType.BOTH);

            assertThatThrownBy(() ->
                SubscriptionTransitionPolicy.verifySubscribe(member, channel, SubscriptionStatus.PREMIUM))
                .isInstanceOf(InvalidSubscribeTransitionException.class);
        }

        @Test
        @DisplayName("우선순위: 채널 권한 검증이 먼저 (UNSUBSCRIBE_ONLY + 잘못된 전이 → 채널 에러)")
        void channelCheckedFirst() {
            Member member = memberWith(SubscriptionStatus.PREMIUM);
            Channel channel = channelWith(ChannelType.UNSUBSCRIBE_ONLY);

            assertThatThrownBy(() ->
                SubscriptionTransitionPolicy.verifySubscribe(member, channel, SubscriptionStatus.PREMIUM))
                .isInstanceOf(ChannelSubscribeNotAllowedException.class);
        }
    }

    @Nested
    @DisplayName("T-4. 해지 검증")
    class VerifyUnsubscribe {

        @Test
        @DisplayName("BOTH 채널 + 허용 전이 → 통과")
        void allowed() {
            Member member = memberWith(SubscriptionStatus.PREMIUM);
            Channel channel = channelWith(ChannelType.BOTH);

            assertThatCode(() ->
                SubscriptionTransitionPolicy.verifyUnsubscribe(member, channel, SubscriptionStatus.NONE))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SUBSCRIBE_ONLY 채널은 ChannelUnsubscribeNotAllowedException")
        void channelDenied() {
            Member member = memberWith(SubscriptionStatus.PREMIUM);
            Channel channel = channelWith(ChannelType.SUBSCRIBE_ONLY);

            assertThatThrownBy(() ->
                SubscriptionTransitionPolicy.verifyUnsubscribe(member, channel, SubscriptionStatus.NONE))
                .isInstanceOf(ChannelUnsubscribeNotAllowedException.class)
                .satisfies(e -> assertThat(((ChannelUnsubscribeNotAllowedException) e).errorCode())
                    .isEqualTo(SubscriptionErrorCode.CHANNEL_UNSUBSCRIBE_NOT_ALLOWED));
        }

        @Test
        @DisplayName("NONE 회원은 해지 불가 → InvalidUnsubscribeTransitionException")
        void transitionDenied() {
            Member member = memberWith(SubscriptionStatus.NONE);
            Channel channel = channelWith(ChannelType.BOTH);

            assertThatThrownBy(() ->
                SubscriptionTransitionPolicy.verifyUnsubscribe(member, channel, SubscriptionStatus.NONE))
                .isInstanceOf(InvalidUnsubscribeTransitionException.class);
        }
    }
}
