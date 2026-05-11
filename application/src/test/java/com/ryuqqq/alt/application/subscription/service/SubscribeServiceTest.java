package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.coordinator.MemberRegistrationCoordinator;
import com.ryuqqq.alt.application.subscription.coordinator.SubscribeCoordinator;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.factory.SubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.SubscribeFactory;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.port.out.IdempotencyRegistryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscribeService — 구독 UseCase 단위 테스트")
class SubscribeServiceTest {

    @Mock SubscribeFactory subscribeFactory;
    @Mock MemberRegistrationCoordinator memberRegistrationCoordinator;
    @Mock ChannelReadManager channelReadManager;
    @Mock SubscribeCoordinator subscribeCoordinator;
    @Mock IdempotencyRegistryPort idempotencyRegistry;

    @InjectMocks SubscribeService service;

    private static final PhoneNumber PHONE = PhoneNumber.of("01012345678");
    private static final ChannelId CHANNEL_ID = ChannelId.of(13L);
    private static final String IDEMPOTENCY_KEY = "test-key-001";

    @BeforeEach
    void stubIdempotencyPassThrough() {
        // 단위 테스트에서는 idempotency 게이트가 supplier 를 그대로 실행하도록 통과시킨다.
        // 게이트 자체의 동작은 Caffeine 어댑터 단위 테스트에서 검증.
        given(idempotencyRegistry.executeOnce(anyString(), any(Supplier.class)))
            .willAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    }

    private static SubscribeCommand subscribeCommand(SubscriptionStatus target) {
        return SubscribeCommand.of(PHONE, CHANNEL_ID, target, IDEMPOTENCY_KEY);
    }

    /** Factory 가 만드는 초기 번들을 흉내내는 헬퍼 — Service 흐름과 동일한 모양으로 빌드한다. */
    private static SubscribeBundle initialBundle(Member draftMember, SubscriptionStatus target) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            draftMember.id(), CHANNEL_ID, AttemptKind.SUBSCRIBE,
            draftMember.status(), target,
            now, now, IDEMPOTENCY_KEY
        );
        return new SubscribeBundle(draftMember, null, attempt);
    }

    @Nested
    @DisplayName("해피 패스")
    class Success {

        @Test
        @DisplayName("NONE → PREMIUM 구독 시 factory → memberCoordinator → channelReadManager → subscribeCoordinator 순으로 호출되고 결과가 그대로 반환된다")
        void shouldOrchestrateAllStepsInOrderAndReturnCoordinatorResult() {
            // given
            SubscribeCommand command = subscribeCommand(SubscriptionStatus.PREMIUM);
            Member draftMember = MemberFixture.newMember();
            SubscribeBundle initial = initialBundle(draftMember, SubscriptionStatus.PREMIUM);

            Member persistedMember = Member.reconstitute(MemberId.of(99L), PHONE, SubscriptionStatus.NONE);
            Channel channel = ChannelFixture.bothChannel();
            SubscribeResult expected = new SubscribeResult(
                7L, AttemptStatus.COMMITTED, SubscriptionStatus.PREMIUM, null
            );

            given(subscribeFactory.createBundle(command)).willReturn(initial);
            given(memberRegistrationCoordinator.findOrRegister(initial.member())).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            given(subscribeCoordinator.coordinate(any(SubscribeBundle.class))).willReturn(expected);

            // when
            SubscribeResult actual = service.execute(command);

            // then
            assertThat(actual).isSameAs(expected);

            InOrder order = inOrder(subscribeFactory, memberRegistrationCoordinator, channelReadManager, subscribeCoordinator);
            order.verify(subscribeFactory).createBundle(command);
            order.verify(memberRegistrationCoordinator).findOrRegister(initial.member());
            order.verify(channelReadManager).getById(CHANNEL_ID);
            order.verify(subscribeCoordinator).coordinate(any(SubscribeBundle.class));
            order.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("subscribeCoordinator 에 전달되는 번들은 영속화된 Member 와 fetch 된 Channel 이 주입된 상태이다")
        void shouldPassBundleWithPersistedMemberAndFetchedChannelToCoordinator() {
            // given
            SubscribeCommand command = subscribeCommand(SubscriptionStatus.PREMIUM);
            Member draftMember = MemberFixture.newMember();
            SubscribeBundle initial = initialBundle(draftMember, SubscriptionStatus.PREMIUM);

            Member persistedMember = Member.reconstitute(MemberId.of(42L), PHONE, SubscriptionStatus.NONE);
            Channel channel = ChannelFixture.bothChannel();
            SubscribeResult expected = new SubscribeResult(
                1L, AttemptStatus.COMMITTED, SubscriptionStatus.PREMIUM, null
            );

            given(subscribeFactory.createBundle(command)).willReturn(initial);
            given(memberRegistrationCoordinator.findOrRegister(initial.member())).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            when(subscribeCoordinator.coordinate(any(SubscribeBundle.class))).thenAnswer(invocation -> {
                SubscribeBundle passed = invocation.getArgument(0);
                assertThat(passed.member()).isSameAs(persistedMember);
                assertThat(passed.channel()).isSameAs(channel);
                // attempt.memberId 는 persisted member id 로 동기화된다
                assertThat(passed.attempt().memberId()).isEqualTo(persistedMember.id());
                return expected;
            });

            // when
            service.execute(command);

            // then
            verify(subscribeCoordinator, times(1)).coordinate(any(SubscribeBundle.class));
        }
    }

    @Nested
    @DisplayName("Registration-Only 분기 (target=NONE)")
    class RegistrationOnly {

        @Test
        @DisplayName("target=NONE 이면 channelReadManager / subscribeCoordinator 는 호출되지 않고 registrationOnly 결과가 반환된다")
        void shouldReturnRegistrationOnlyWithoutFetchingChannelOrCoordinating() {
            // given
            SubscribeCommand command = subscribeCommand(SubscriptionStatus.NONE);
            Member draftMember = MemberFixture.newMember();
            SubscribeBundle initial = initialBundle(draftMember, SubscriptionStatus.NONE);

            Member persistedMember = Member.reconstitute(MemberId.of(7L), PHONE, SubscriptionStatus.NONE);

            given(subscribeFactory.createBundle(command)).willReturn(initial);
            given(memberRegistrationCoordinator.findOrRegister(initial.member())).willReturn(persistedMember);

            // when
            SubscribeResult result = service.execute(command);

            // then
            assertThat(result.attemptId()).isNull();
            assertThat(result.status()).isNull();
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isNull();

            verifyNoInteractions(channelReadManager);
            verifyNoInteractions(subscribeCoordinator);
        }
    }

    @Nested
    @DisplayName("실패 시나리오")
    class Failure {

        @Test
        @DisplayName("channelReadManager 가 ChannelNotFoundException 을 던지면 그대로 전파되고 subscribeCoordinator 는 호출되지 않는다")
        void shouldPropagateChannelNotFoundAndSkipCoordinator() {
            // given
            SubscribeCommand command = subscribeCommand(SubscriptionStatus.PREMIUM);
            Member draftMember = MemberFixture.newMember();
            SubscribeBundle initial = initialBundle(draftMember, SubscriptionStatus.PREMIUM);

            Member persistedMember = Member.reconstitute(MemberId.of(7L), PHONE, SubscriptionStatus.NONE);

            given(subscribeFactory.createBundle(command)).willReturn(initial);
            given(memberRegistrationCoordinator.findOrRegister(initial.member())).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID))
                .willThrow(new ChannelNotFoundException("channelId=" + CHANNEL_ID.value()));

            // when & then
            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(ChannelNotFoundException.class);

            verify(subscribeCoordinator, never()).coordinate(any(SubscribeBundle.class));
        }

        @Test
        @DisplayName("verifyTransition 이 도메인 예외를 던지면 subscribeCoordinator 는 호출되지 않고 예외가 그대로 전파된다")
        void shouldPropagateTransitionExceptionAndSkipCoordinator() {
            // given — 이미 PREMIUM 인 회원이 다시 PREMIUM 을 신청하면 InvalidSubscribeTransitionException
            SubscribeCommand command = subscribeCommand(SubscriptionStatus.PREMIUM);
            Member draftMember = MemberFixture.newMember();
            SubscribeBundle initial = initialBundle(draftMember, SubscriptionStatus.PREMIUM);

            Member persistedPremiumMember = MemberFixture.premiumMember();
            Channel channel = ChannelFixture.bothChannel();

            given(subscribeFactory.createBundle(command)).willReturn(initial);
            given(memberRegistrationCoordinator.findOrRegister(initial.member())).willReturn(persistedPremiumMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);

            // when & then
            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(InvalidSubscribeTransitionException.class);

            verify(subscribeCoordinator, never()).coordinate(any(SubscribeBundle.class));
        }
    }
}
