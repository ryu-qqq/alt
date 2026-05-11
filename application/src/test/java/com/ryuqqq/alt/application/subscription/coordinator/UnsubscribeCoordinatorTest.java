package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeBundleFixture;
import com.ryuqqq.alt.application.subscription.manager.RandomClientManager;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnsubscribeCoordinator — 해지 외부 호출 + 결과 기록 코디네이터 단위 테스트")
class UnsubscribeCoordinatorTest {

    @Mock RandomClientManager randomClientManager;
    @Mock SubscriptionPersistenceFacade subscriptionPersistenceFacade;

    @InjectMocks UnsubscribeCoordinator coordinator;

    private UnsubscribeBundle readyBundle() {
        // PREMIUM → NONE 해지 시나리오
        Member member = MemberFixture.premiumMember();
        Channel channel = ChannelFixture.bothChannel();
        return UnsubscribeBundleFixture.ready(member, channel, SubscriptionStatus.NONE);
    }

    private void stubFacadeReturnsPersisted() {
        lenient().when(subscriptionPersistenceFacade.saveWithMemberUpdate(any(), any()))
            .thenAnswer(inv -> ((SubscriptionAttempt) inv.getArgument(1))
                .withId(com.ryuqqq.alt.domain.subscription.AttemptId.of(42L)));
        lenient().when(subscriptionPersistenceFacade.saveAttempt(any()))
            .thenAnswer(inv -> ((SubscriptionAttempt) inv.getArgument(0))
                .withId(com.ryuqqq.alt.domain.subscription.AttemptId.of(42L)));
    }

    @Nested
    @DisplayName("APPROVED — 외부 진행 신호")
    class Approved {

        @Test
        @DisplayName("APPROVED 면 applyApproved → saveWithMemberUpdate 가 호출되고 member 가 NONE 으로 갱신된 COMMITTED 결과가 반환된다")
        void shouldApplyApprovedAndPersistMemberAndAttempt() {
            // given
            UnsubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.APPROVED);
            stubFacadeReturnsPersisted();

            // when
            UnsubscribeResult result = coordinator.coordinate(bundle);

            // then
            InOrder order = inOrder(randomClientManager, subscriptionPersistenceFacade);
            order.verify(randomClientManager).call();
            order.verify(subscriptionPersistenceFacade).saveWithMemberUpdate(any(Member.class), any(SubscriptionAttempt.class));

            verify(subscriptionPersistenceFacade, never()).saveAttempt(any());

            assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("APPROVED 케이스에서 saveWithMemberUpdate 에 전달되는 member 는 NONE 으로 갱신되어 있다")
        void shouldPassUpdatedMemberToFacade() {
            // given
            UnsubscribeBundle bundle = readyBundle();
            assertThat(bundle.member().status()).isEqualTo(SubscriptionStatus.PREMIUM); // 가정 검증
            given(randomClientManager.call()).willReturn(ExternalCallResult.APPROVED);
            stubFacadeReturnsPersisted();

            // when
            coordinator.coordinate(bundle);

            // then
            org.mockito.ArgumentCaptor<Member> memberCaptor = org.mockito.ArgumentCaptor.forClass(Member.class);
            verify(subscriptionPersistenceFacade).saveWithMemberUpdate(memberCaptor.capture(), any());
            assertThat(memberCaptor.getValue().status()).isEqualTo(SubscriptionStatus.NONE);
        }
    }

    @Nested
    @DisplayName("REJECTED — 외부 거절 신호")
    class Rejected {

        @Test
        @DisplayName("REJECTED 면 applyRejected → saveAttempt 만 호출되고 member 영속은 호출되지 않는다")
        void shouldApplyRejectedAndPersistAttemptOnly() {
            // given
            UnsubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.REJECTED);
            stubFacadeReturnsPersisted();

            // when
            UnsubscribeResult result = coordinator.coordinate(bundle);

            // then
            verify(subscriptionPersistenceFacade).saveAttempt(any(SubscriptionAttempt.class));
            verify(subscriptionPersistenceFacade, never()).saveWithMemberUpdate(any(), any());

            assertThat(result.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            // member 상태는 변경되지 않음 (PREMIUM 유지)
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED.name());
        }
    }

    @Nested
    @DisplayName("FAILED — 외부 호출 실패")
    class Failed {

        @Test
        @DisplayName("RandomClientException 발생 시 applyFailed → saveAttempt 만 호출되고 결과는 FAILED 이다")
        void shouldApplyFailedAndPersistAttemptOnly() {
            // given
            UnsubscribeBundle bundle = readyBundle();
            given(randomClientManager.call())
                .willThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "HTTP 503"));
            stubFacadeReturnsPersisted();

            // when
            UnsubscribeResult result = coordinator.coordinate(bundle);

            // then
            verify(subscriptionPersistenceFacade).saveAttempt(any(SubscriptionAttempt.class));
            verify(subscriptionPersistenceFacade, never()).saveWithMemberUpdate(any(), any());

            assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_SERVER_ERROR.name());
        }

        @Test
        @DisplayName("FAILED 케이스에서 reason / detail 이 attempt 에 그대로 박제된다")
        void shouldPreserveReasonAndDetailInAttempt() {
            // given
            UnsubscribeBundle bundle = readyBundle();
            given(randomClientManager.call())
                .willThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_PARSE_FAILURE, "JSON parse error"));
            stubFacadeReturnsPersisted();

            // when
            UnsubscribeResult result = coordinator.coordinate(bundle);

            // then
            org.mockito.ArgumentCaptor<SubscriptionAttempt> captor = org.mockito.ArgumentCaptor.forClass(SubscriptionAttempt.class);
            verify(subscriptionPersistenceFacade).saveAttempt(captor.capture());
            SubscriptionAttempt persisted = captor.getValue();
            assertThat(persisted.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(persisted.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_PARSE_FAILURE);
            assertThat(persisted.failureDetail()).isEqualTo("JSON parse error");
        }
    }
}
