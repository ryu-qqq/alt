package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.error.ChannelUnsubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UnsubscribeBundle — 해지 번들 단위 테스트")
class UnsubscribeBundleTest {

    @Nested
    @DisplayName("withChannel")
    class WithChannel {

        @Test
        @DisplayName("주입된 channel 만 갈아끼운 새 번들을 반환하고 member/attempt 는 보존된다")
        void shouldOnlyReplaceChannel() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.initial(member, SubscriptionStatus.NONE);
            assertThat(bundle.channel()).isNull();

            Channel channel = ChannelFixture.bothChannel();

            // when
            UnsubscribeBundle result = bundle.withChannel(channel);

            // then
            assertThat(result.channel()).isSameAs(channel);
            assertThat(result.member()).isSameAs(member);
            assertThat(result.attempt()).isSameAs(bundle.attempt());
        }
    }

    @Nested
    @DisplayName("verifyTransition")
    class VerifyTransition {

        @Test
        @DisplayName("권한 있는 채널 + 유효한 해지 전이는 예외 없이 통과한다")
        void shouldPassWhenAllValid() {
            // given — PREMIUM → NONE
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE);

            // when & then — 예외 없음
            bundle.verifyTransition();
        }

        @Test
        @DisplayName("해지 권한 없는 채널이면 ChannelUnsubscribeNotAllowedException 을 던진다")
        void shouldThrowWhenChannelNotAllowed() {
            // given — SUBSCRIBE_ONLY 채널은 해지 불가
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.subscribeOnlyChannel(), SubscriptionStatus.NONE);

            // when & then
            assertThatThrownBy(bundle::verifyTransition)
                .isInstanceOf(ChannelUnsubscribeNotAllowedException.class);
        }

        @Test
        @DisplayName("이미 NONE 인 회원이 NONE 해지를 시도하면 InvalidUnsubscribeTransitionException")
        void shouldThrowWhenInvalidTransition() {
            // given — NONE 회원은 해지 불가
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE);

            // when & then
            assertThatThrownBy(bundle::verifyTransition)
                .isInstanceOf(InvalidUnsubscribeTransitionException.class);
        }
    }

    @Nested
    @DisplayName("applyApproved")
    class ApplyApproved {

        @Test
        @DisplayName("member 가 toStatus 로 갱신되고 attempt 는 COMMITTED 그대로 유지되며 동일 번들 반환")
        void shouldUpdateMemberAndKeepAttempt() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE);
            SubscriptionAttempt original = bundle.attempt();

            // when
            UnsubscribeBundle result = bundle.applyApproved();

            // then
            assertThat(result).isSameAs(bundle);
            assertThat(result.member().status()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.attempt()).isSameAs(original);
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.COMMITTED);
        }
    }

    @Nested
    @DisplayName("applyRejected")
    class ApplyRejected {

        @Test
        @DisplayName("attempt 가 ROLLED_BACK 으로 변환된 새 번들 반환, member 변경 X")
        void shouldRolledBackAttemptOnly() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE);

            // when
            UnsubscribeBundle result = bundle.applyRejected();

            // then
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(result.attempt().failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(result.member()).isSameAs(member);
            assertThat(result.member().status()).isEqualTo(SubscriptionStatus.PREMIUM);
        }
    }

    @Nested
    @DisplayName("applyFailed")
    class ApplyFailed {

        @Test
        @DisplayName("attempt 가 FAILED 로 변환되고 reason / detail 이 박제된 새 번들 반환")
        void shouldFailAttemptWithReasonAndDetail() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE);

            // when
            UnsubscribeBundle result = bundle.applyFailed(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "503 from csrng");

            // then
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(result.attempt().failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_SERVER_ERROR);
            assertThat(result.attempt().failureDetail()).isEqualTo("503 from csrng");
            assertThat(result.member()).isSameAs(member);
        }
    }

    @Nested
    @DisplayName("toResult")
    class ToResult {

        @Test
        @DisplayName("APPROVED 적용 후 toResult 는 COMMITTED + 갱신된 currentStatus 의 UnsubscribeResult")
        void shouldBuildResultAfterApproved() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE)
                .applyApproved();

            // when
            UnsubscribeResult result = bundle.toResult();

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("REJECTED 적용 후 toResult 는 ROLLED_BACK + EXTERNAL_REJECTED reason 의 UnsubscribeResult")
        void shouldBuildResultAfterRejected() {
            // given
            Member member = MemberFixture.premiumMember();
            UnsubscribeBundle bundle = UnsubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.NONE)
                .applyRejected();

            // when
            UnsubscribeResult result = bundle.toResult();

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED.name());
        }
    }
}
