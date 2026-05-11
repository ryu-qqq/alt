package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscribeBundle — 구독 번들 단위 테스트")
class SubscribeBundleTest {

    @Nested
    @DisplayName("withMember — persisted member 주입")
    class WithMember {

        @Test
        @DisplayName("persisted member 의 id 로 attempt.memberId 가 동기화된 새 번들을 반환한다")
        void shouldSyncAttemptMemberIdWithPersistedMember() {
            // given — factory 직후 모양 (id=null)
            SubscribeBundle initial = SubscribeBundleFixture.initial(SubscriptionStatus.PREMIUM);
            assertThat(initial.member().id().isNew()).isTrue();
            assertThat(initial.attempt().memberId().isNew()).isTrue();

            Member persisted = MemberFixture.reconstituted(99L, SubscriptionStatus.NONE);

            // when
            SubscribeBundle result = initial.withMember(persisted);

            // then
            assertThat(result.member()).isSameAs(persisted);
            assertThat(result.attempt().memberId()).isEqualTo(MemberId.of(99L));
            assertThat(result.channel()).isNull(); // 보존
        }
    }

    @Nested
    @DisplayName("withChannel — channel 주입")
    class WithChannel {

        @Test
        @DisplayName("주입된 channel 만 갈아끼운 새 번들을 반환하고 member/attempt 는 보존된다")
        void shouldOnlyReplaceChannel() {
            // given
            Member persisted = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.withPersistedMember(persisted, SubscriptionStatus.PREMIUM);
            Channel channel = ChannelFixture.bothChannel();

            // when
            SubscribeBundle result = bundle.withChannel(channel);

            // then
            assertThat(result.channel()).isSameAs(channel);
            assertThat(result.member()).isSameAs(persisted);
            assertThat(result.attempt()).isSameAs(bundle.attempt());
        }
    }

    @Nested
    @DisplayName("isRegistrationOnly")
    class IsRegistrationOnly {

        @Test
        @DisplayName("member.status=NONE 이고 attempt.toStatus=NONE 이면 true")
        void shouldBeTrueWhenBothNone() {
            // given — registration-only 시나리오 (NONE → NONE)
            SubscribeBundle bundle = SubscribeBundleFixture.initial(SubscriptionStatus.NONE);

            // when & then
            assertThat(bundle.isRegistrationOnly()).isTrue();
        }

        @Test
        @DisplayName("member.status=NONE 이고 attempt.toStatus=BASIC 이면 false")
        void shouldBeFalseWhenMemberNoneAndAttemptBasic() {
            // given
            SubscribeBundle bundle = SubscribeBundleFixture.initial(SubscriptionStatus.BASIC);

            // when & then
            assertThat(bundle.isRegistrationOnly()).isFalse();
        }

        @Test
        @DisplayName("member.status=BASIC 이고 attempt.toStatus=NONE 이면 false")
        void shouldBeFalseWhenMemberBasicAndAttemptNone() {
            // given — 인위적으로 BASIC member 와 toStatus=NONE attempt 를 묶은 번들
            Member basic = MemberFixture.reconstituted(1L, SubscriptionStatus.BASIC);
            SubscribeBundle bundle = SubscribeBundleFixture.withPersistedMember(basic, SubscriptionStatus.NONE);

            // when & then
            assertThat(bundle.isRegistrationOnly()).isFalse();
        }

        @Test
        @DisplayName("둘 다 BASIC 이면 false")
        void shouldBeFalseWhenBothBasic() {
            // given
            Member basic = MemberFixture.reconstituted(1L, SubscriptionStatus.BASIC);
            SubscribeBundle bundle = SubscribeBundleFixture.withPersistedMember(basic, SubscriptionStatus.BASIC);

            // when & then
            assertThat(bundle.isRegistrationOnly()).isFalse();
        }
    }

    @Nested
    @DisplayName("memberStatus")
    class MemberStatusAccessor {

        @Test
        @DisplayName("member 의 status 를 그대로 노출한다 (1단계 accessor — LoD 준수)")
        void shouldExposeMemberStatus() {
            // given
            SubscribeBundle bundle = SubscribeBundleFixture.initial(SubscriptionStatus.PREMIUM);

            // when & then
            assertThat(bundle.memberStatus()).isEqualTo(SubscriptionStatus.NONE);
        }
    }

    @Nested
    @DisplayName("verifyTransition — 도메인 정책 검증 위임")
    class VerifyTransition {

        @Test
        @DisplayName("권한 있는 채널 + 유효한 전이는 예외 없이 통과한다")
        void shouldPassWhenAllValid() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM);

            // when & then — 예외 없이 통과
            bundle.verifyTransition();
        }

        @Test
        @DisplayName("구독 권한 없는 채널이면 ChannelSubscribeNotAllowedException 을 던진다")
        void shouldThrowWhenChannelNotAllowed() {
            // given — UNSUBSCRIBE_ONLY 채널은 구독 불가
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.unsubscribeOnlyChannel(), SubscriptionStatus.PREMIUM);

            // when & then
            assertThatThrownBy(bundle::verifyTransition)
                .isInstanceOf(ChannelSubscribeNotAllowedException.class);
        }

        @Test
        @DisplayName("이미 PREMIUM 인 회원이 PREMIUM 으로 다시 구독하면 InvalidSubscribeTransitionException 을 던진다")
        void shouldThrowWhenInvalidTransition() {
            // given
            Member premium = MemberFixture.reconstituted(1L, SubscriptionStatus.PREMIUM);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(premium, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM);

            // when & then
            assertThatThrownBy(bundle::verifyTransition)
                .isInstanceOf(InvalidSubscribeTransitionException.class);
        }
    }

    @Nested
    @DisplayName("applyApproved — APPROVED 적용")
    class ApplyApproved {

        @Test
        @DisplayName("member 상태가 toStatus 로 갱신되고 attempt 는 그대로 유지되며 동일 번들이 반환된다")
        void shouldUpdateMemberStatusAndKeepAttempt() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM);
            SubscriptionAttempt originalAttempt = bundle.attempt();

            // when
            SubscribeBundle result = bundle.applyApproved();

            // then
            assertThat(result).isSameAs(bundle); // same instance
            assertThat(result.member().status()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.attempt()).isSameAs(originalAttempt); // attempt 는 COMMITTED 그대로
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.COMMITTED);
        }
    }

    @Nested
    @DisplayName("applyRejected — REJECTED 적용")
    class ApplyRejected {

        @Test
        @DisplayName("attempt 가 ROLLED_BACK 으로 변환된 새 번들이 반환되고 member 는 변경되지 않는다")
        void shouldRolledBackAttemptOnly() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM);

            // when
            SubscribeBundle result = bundle.applyRejected();

            // then
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(result.attempt().failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(result.member().status()).isEqualTo(SubscriptionStatus.NONE); // 변경 X
            assertThat(result.member()).isSameAs(member);
        }
    }

    @Nested
    @DisplayName("applyFailed — 외부 호출 실패 적용")
    class ApplyFailed {

        @Test
        @DisplayName("attempt 가 FAILED 로 변환되고 reason / detail 이 박제된 새 번들이 반환된다")
        void shouldFailAttemptWithReasonAndDetail() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM);

            // when
            SubscribeBundle result = bundle.applyFailed(AttemptFailureReason.EXTERNAL_TIMEOUT, "timed out 2s");

            // then
            assertThat(result.attempt().status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(result.attempt().failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT);
            assertThat(result.attempt().failureDetail()).isEqualTo("timed out 2s");
            assertThat(result.member()).isSameAs(member);
        }
    }

    @Nested
    @DisplayName("toResult")
    class ToResult {

        @Test
        @DisplayName("APPROVED 적용 후 toResult 는 attempt + member.status 로 SubscribeResult 를 만든다")
        void shouldBuildResultAfterApproved() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM)
                .applyApproved();

            // when
            SubscribeResult result = bundle.toResult();

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("FAILED 적용 후 toResult 는 failureReason 이 박제된 SubscribeResult 를 만든다")
        void shouldBuildResultAfterFailed() {
            // given
            Member member = MemberFixture.reconstituted(1L, SubscriptionStatus.NONE);
            SubscribeBundle bundle = SubscribeBundleFixture.ready(member, ChannelFixture.bothChannel(), SubscriptionStatus.PREMIUM)
                .applyFailed(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN, "open");

            // when
            SubscribeResult result = bundle.toResult();

            // then
            assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN.name());
        }
    }
}
