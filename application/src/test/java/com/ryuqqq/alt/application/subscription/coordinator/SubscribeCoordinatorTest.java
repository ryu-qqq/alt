package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.SubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.SubscribeBundleFixture;
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
@DisplayName("SubscribeCoordinator — 외부 호출 + 결과 기록 코디네이터 단위 테스트")
class SubscribeCoordinatorTest {

    @Mock RandomClientManager randomClientManager;
    @Mock SubscriptionPersistenceFacade subscriptionPersistenceFacade;

    @InjectMocks SubscribeCoordinator coordinator;

    private SubscribeBundle readyBundle() {
        Member member = MemberFixture.reconstitutedMember();
        Channel channel = ChannelFixture.bothChannel();
        return SubscribeBundleFixture.ready(member, channel, SubscriptionStatus.PREMIUM);
    }

    /**
     * facade.persist 가 attemptId 가 채워진 attempt 를 반환하도록 stub.
     * lenient — 테스트마다 saveWithMemberUpdate / saveAttempt 중 하나만 호출되므로 strict mode 가 unnecessary stubbing 으로 거절하는 것을 회피.
     */
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
        @DisplayName("APPROVED 면 applyApproved → saveWithMemberUpdate 가 순서대로 호출되고 COMMITTED 결과가 반환된다")
        void shouldApplyApprovedAndPersistMemberAndAttempt() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.APPROVED);
            stubFacadeReturnsPersisted();

            // when
            SubscribeResult result = coordinator.coordinate(bundle);

            // then — APPROVED 적용 후 member 상태가 PREMIUM 이 되고 saveWithMemberUpdate 호출
            InOrder order = inOrder(randomClientManager, subscriptionPersistenceFacade);
            order.verify(randomClientManager).call();
            order.verify(subscriptionPersistenceFacade).saveWithMemberUpdate(any(Member.class), any(SubscriptionAttempt.class));

            verify(subscriptionPersistenceFacade, never()).saveAttempt(any());

            assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("APPROVED 케이스에서 saveWithMemberUpdate 에 전달되는 member 는 PREMIUM 으로 갱신되어 있다")
        void shouldPassPremiumUpdatedMemberToFacade() {
            // given
            SubscribeBundle bundle = readyBundle();
            assertThat(bundle.member().status()).isEqualTo(SubscriptionStatus.NONE); // 가정 검증
            given(randomClientManager.call()).willReturn(ExternalCallResult.APPROVED);
            stubFacadeReturnsPersisted();

            // when
            coordinator.coordinate(bundle);

            // then
            org.mockito.ArgumentCaptor<Member> memberCaptor = org.mockito.ArgumentCaptor.forClass(Member.class);
            org.mockito.ArgumentCaptor<SubscriptionAttempt> attemptCaptor = org.mockito.ArgumentCaptor.forClass(SubscriptionAttempt.class);
            verify(subscriptionPersistenceFacade).saveWithMemberUpdate(memberCaptor.capture(), attemptCaptor.capture());

            assertThat(memberCaptor.getValue().status()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(attemptCaptor.getValue().status()).isEqualTo(AttemptStatus.COMMITTED);
        }
    }

    @Nested
    @DisplayName("REJECTED — 외부 거절 신호")
    class Rejected {

        @Test
        @DisplayName("REJECTED 면 applyRejected → saveAttempt 만 호출되고 member 영속은 호출되지 않는다")
        void shouldApplyRejectedAndPersistAttemptOnly() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.REJECTED);
            stubFacadeReturnsPersisted();

            // when
            SubscribeResult result = coordinator.coordinate(bundle);

            // then
            verify(subscriptionPersistenceFacade).saveAttempt(any(SubscriptionAttempt.class));
            verify(subscriptionPersistenceFacade, never()).saveWithMemberUpdate(any(), any());

            assertThat(result.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            // member 상태는 변경되지 않음 (NONE 유지)
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED.name());
        }

        @Test
        @DisplayName("REJECTED 케이스에서 saveAttempt 에 전달되는 attempt 는 ROLLED_BACK 상태이다")
        void shouldPassRolledBackAttemptToFacade() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.REJECTED);
            stubFacadeReturnsPersisted();

            // when
            coordinator.coordinate(bundle);

            // then
            org.mockito.ArgumentCaptor<SubscriptionAttempt> captor = org.mockito.ArgumentCaptor.forClass(SubscriptionAttempt.class);
            verify(subscriptionPersistenceFacade).saveAttempt(captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(AttemptStatus.ROLLED_BACK);
        }

        @Test
        @DisplayName("REJECTED 결과 attemptId 는 facade 가 반환한 persistedId 와 일치한다")
        void shouldReturnPersistedAttemptId() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call()).willReturn(ExternalCallResult.REJECTED);
            stubFacadeReturnsPersisted();

            // when
            SubscribeResult result = coordinator.coordinate(bundle);

            // then
            assertThat(result.attemptId()).isEqualTo(42L);
        }
    }

    @Nested
    @DisplayName("FAILED — 외부 호출 실패 (RandomClientException)")
    class Failed {

        @Test
        @DisplayName("RandomClientException 발생 시 applyFailed → saveAttempt 만 호출되고 결과는 FAILED 이다")
        void shouldApplyFailedAndPersistAttemptOnly() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call())
                .willThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_TIMEOUT, "HTTP timeout 2000ms"));
            stubFacadeReturnsPersisted();

            // when
            SubscribeResult result = coordinator.coordinate(bundle);

            // then
            verify(subscriptionPersistenceFacade).saveAttempt(any(SubscriptionAttempt.class));
            verify(subscriptionPersistenceFacade, never()).saveWithMemberUpdate(any(), any());

            assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
            // member 상태 변경 없음
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT.name());
        }

        @Test
        @DisplayName("FAILED 케이스에서 reason / detail 이 attempt 에 그대로 박제된다")
        void shouldPreserveReasonAndDetailInAttempt() {
            // given
            SubscribeBundle bundle = readyBundle();
            given(randomClientManager.call())
                .willThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN, "CB open at 12:00"));
            stubFacadeReturnsPersisted();

            // when
            coordinator.coordinate(bundle);

            // then
            org.mockito.ArgumentCaptor<SubscriptionAttempt> captor = org.mockito.ArgumentCaptor.forClass(SubscriptionAttempt.class);
            verify(subscriptionPersistenceFacade).saveAttempt(captor.capture());
            SubscriptionAttempt persisted = captor.getValue();
            assertThat(persisted.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(persisted.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN);
            assertThat(persisted.failureDetail()).isEqualTo("CB open at 12:00");
        }
    }
}
