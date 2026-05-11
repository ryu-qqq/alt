package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.coordinator.UnsubscribeCoordinator;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeFactory;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.manager.MemberReadManager;
import com.ryuqqq.alt.application.subscription.port.out.IdempotencyRegistryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnsubscribeService — 해지 UseCase 단위 테스트")
class UnsubscribeServiceTest {

    @Mock UnsubscribeFactory unsubscribeFactory;
    @Mock MemberReadManager memberReadManager;
    @Mock ChannelReadManager channelReadManager;
    @Mock UnsubscribeCoordinator unsubscribeCoordinator;
    @Mock IdempotencyRegistryPort idempotencyRegistry;

    @InjectMocks UnsubscribeService service;

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

    private static UnsubscribeCommand unsubscribeCommand(SubscriptionStatus target) {
        return UnsubscribeCommand.of(PHONE, CHANNEL_ID, target, IDEMPOTENCY_KEY);
    }

    /** UnsubscribeFactory 가 만드는 초기 번들을 흉내내는 헬퍼 — Service 흐름과 동일한 모양으로 빌드한다. */
    private static UnsubscribeBundle initialBundle(Member persistedMember, SubscriptionStatus target) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            persistedMember.id(), CHANNEL_ID, AttemptKind.UNSUBSCRIBE,
            persistedMember.status(), target,
            now, now, IDEMPOTENCY_KEY
        );
        return new UnsubscribeBundle(persistedMember, null, attempt);
    }

    @Nested
    @DisplayName("해피 패스")
    class Success {

        @Test
        @DisplayName("PREMIUM → NONE 해지 시 memberReadManager → channelReadManager → factory → unsubscribeCoordinator 순으로 호출되고 결과가 그대로 반환된다")
        void shouldOrchestrateAllStepsInOrderAndReturnCoordinatorResult() {
            // given
            UnsubscribeCommand command = unsubscribeCommand(SubscriptionStatus.NONE);
            Member persistedMember = MemberFixture.premiumMember();
            Channel channel = ChannelFixture.bothChannel();
            UnsubscribeBundle initial = initialBundle(persistedMember, SubscriptionStatus.NONE);

            UnsubscribeResult expected = new UnsubscribeResult(
                3L, AttemptStatus.COMMITTED, SubscriptionStatus.NONE, null
            );

            given(memberReadManager.getByPhoneNumber(PHONE)).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            given(unsubscribeFactory.createBundle(persistedMember, command)).willReturn(initial);
            given(unsubscribeCoordinator.coordinate(any(UnsubscribeBundle.class))).willReturn(expected);

            // when
            UnsubscribeResult actual = service.execute(command);

            // then
            assertThat(actual).isSameAs(expected);

            InOrder order = inOrder(memberReadManager, channelReadManager, unsubscribeFactory, unsubscribeCoordinator);
            order.verify(memberReadManager).getByPhoneNumber(PHONE);
            order.verify(channelReadManager).getById(CHANNEL_ID);
            order.verify(unsubscribeFactory).createBundle(persistedMember, command);
            order.verify(unsubscribeCoordinator).coordinate(any(UnsubscribeBundle.class));
            order.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("unsubscribeCoordinator 에 전달되는 번들에는 fetch 된 Channel 이 주입되어 있다")
        void shouldPassBundleWithChannelToCoordinator() {
            // given
            UnsubscribeCommand command = unsubscribeCommand(SubscriptionStatus.BASIC);
            Member persistedMember = MemberFixture.premiumMember();
            Channel channel = ChannelFixture.bothChannel();
            UnsubscribeBundle initial = initialBundle(persistedMember, SubscriptionStatus.BASIC);

            UnsubscribeResult expected = new UnsubscribeResult(
                5L, AttemptStatus.COMMITTED, SubscriptionStatus.BASIC, null
            );

            given(memberReadManager.getByPhoneNumber(PHONE)).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            given(unsubscribeFactory.createBundle(persistedMember, command)).willReturn(initial);
            when(unsubscribeCoordinator.coordinate(any(UnsubscribeBundle.class))).thenAnswer(invocation -> {
                UnsubscribeBundle passed = invocation.getArgument(0);
                assertThat(passed.member()).isSameAs(persistedMember);
                assertThat(passed.channel()).isSameAs(channel);
                return expected;
            });

            // when
            service.execute(command);

            // then
            verify(unsubscribeCoordinator, times(1)).coordinate(any(UnsubscribeBundle.class));
        }
    }

    @Nested
    @DisplayName("실패 시나리오")
    class Failure {

        @Test
        @DisplayName("회원이 존재하지 않으면 MemberNotFoundException 이 전파되고 후속 단계는 호출되지 않는다")
        void shouldPropagateMemberNotFoundAndSkipDownstream() {
            // given
            UnsubscribeCommand command = unsubscribeCommand(SubscriptionStatus.NONE);
            given(memberReadManager.getByPhoneNumber(PHONE))
                .willThrow(new MemberNotFoundException("phoneNumber=" + PHONE.value()));

            // when & then
            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(MemberNotFoundException.class);

            verifyNoInteractions(channelReadManager);
            verifyNoInteractions(unsubscribeFactory);
            verifyNoInteractions(unsubscribeCoordinator);
        }

        @Test
        @DisplayName("verifyTransition 실패 시 unsubscribeCoordinator 는 호출되지 않고 도메인 예외가 그대로 전파된다")
        void shouldPropagateTransitionExceptionAndSkipCoordinator() {
            // given — NONE 상태인 회원이 NONE 으로 해지를 시도하면 InvalidUnsubscribeTransitionException
            UnsubscribeCommand command = unsubscribeCommand(SubscriptionStatus.NONE);
            Member noneMember = MemberFixture.reconstitutedMember(); // status=NONE
            Channel channel = ChannelFixture.bothChannel();
            UnsubscribeBundle initial = initialBundle(noneMember, SubscriptionStatus.NONE);

            given(memberReadManager.getByPhoneNumber(PHONE)).willReturn(noneMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            given(unsubscribeFactory.createBundle(noneMember, command)).willReturn(initial);

            // when & then
            assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(InvalidUnsubscribeTransitionException.class);

            verify(unsubscribeCoordinator, never()).coordinate(any(UnsubscribeBundle.class));
        }

        @Test
        @DisplayName("memberReadManager 가 정상 처리되면 동일한 phoneNumber 로 조회가 호출된다")
        void shouldQueryMemberByCommandPhoneNumber() {
            // given — 흐름 검증 (정상 회원 + 정상 채널 + 유효 전이)
            UnsubscribeCommand command = unsubscribeCommand(SubscriptionStatus.NONE);
            Member persistedMember = MemberFixture.premiumMember();
            Channel channel = ChannelFixture.bothChannel();
            UnsubscribeBundle initial = initialBundle(persistedMember, SubscriptionStatus.NONE);

            given(memberReadManager.getByPhoneNumber(PHONE)).willReturn(persistedMember);
            given(channelReadManager.getById(CHANNEL_ID)).willReturn(channel);
            given(unsubscribeFactory.createBundle(persistedMember, command)).willReturn(initial);
            given(unsubscribeCoordinator.coordinate(any(UnsubscribeBundle.class)))
                .willReturn(new UnsubscribeResult(1L, AttemptStatus.COMMITTED, SubscriptionStatus.NONE, null));

            // when
            service.execute(command);

            // then
            verify(memberReadManager).getByPhoneNumber(eq(PHONE));
            verify(channelReadManager).getById(eq(CHANNEL_ID));
        }
    }
}
