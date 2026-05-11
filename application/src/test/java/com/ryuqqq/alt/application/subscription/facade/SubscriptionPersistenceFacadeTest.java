package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptCommandPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionPersistenceFacade — 사가 영속 묶음 단위 테스트")
class SubscriptionPersistenceFacadeTest {

    @Mock MemberCommandPort memberCommandPort;
    @Mock SubscriptionAttemptCommandPort subscriptionAttemptCommandPort;

    @InjectMocks SubscriptionPersistenceFacade facade;

    @Nested
    @DisplayName("saveWithMemberUpdate — APPROVED 케이스")
    class SaveWithMemberUpdate {

        @Test
        @DisplayName("memberCommandPort.persist → subscriptionAttemptCommandPort.persist 순서로 호출된다")
        void shouldPersistMemberThenAttemptInOrder() {
            // given
            Member member = MemberFixture.basicMember();
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.committedSubscribe();
            given(subscriptionAttemptCommandPort.persist(attempt)).willReturn(42L);

            // when
            facade.saveWithMemberUpdate(member, attempt);

            // then
            InOrder order = inOrder(memberCommandPort, subscriptionAttemptCommandPort);
            order.verify(memberCommandPort).persist(member);
            order.verify(subscriptionAttemptCommandPort).persist(attempt);
            order.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("두 port 모두 정확히 1회씩 호출된다")
        void shouldCallEachPortExactlyOnce() {
            // given
            Member member = MemberFixture.premiumMember();
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.committedSubscribe();
            given(subscriptionAttemptCommandPort.persist(attempt)).willReturn(100L);

            // when
            facade.saveWithMemberUpdate(member, attempt);

            // then
            verify(memberCommandPort, times(1)).persist(member);
            verify(subscriptionAttemptCommandPort, times(1)).persist(attempt);
        }

        @Test
        @DisplayName("반환값은 DB 채번 attemptId 가 주입된 SubscriptionAttempt")
        void shouldReturnAttemptWithPersistedId() {
            // given
            Member member = MemberFixture.basicMember();
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.committedSubscribe();
            given(subscriptionAttemptCommandPort.persist(attempt)).willReturn(99L);

            // when
            SubscriptionAttempt persisted = facade.saveWithMemberUpdate(member, attempt);

            // then
            assertThat(persisted.idValue()).isEqualTo(99L);
        }
    }

    @Nested
    @DisplayName("saveAttempt — REJECTED / FAILED 케이스")
    class SaveAttempt {

        @Test
        @DisplayName("attempt 만 영속화하고 memberCommandPort 는 호출하지 않는다")
        void shouldPersistAttemptAndSkipMember() {
            // given
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.rolledBackSubscribe();
            given(subscriptionAttemptCommandPort.persist(attempt)).willReturn(7L);

            // when
            facade.saveAttempt(attempt);

            // then
            verify(subscriptionAttemptCommandPort, times(1)).persist(attempt);
            verify(memberCommandPort, never()).persist(any());
            verifyNoInteractions(memberCommandPort);
        }

        @Test
        @DisplayName("반환값은 DB 채번 attemptId 가 주입된 SubscriptionAttempt")
        void shouldReturnAttemptWithPersistedId() {
            // given
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.rolledBackSubscribe();
            given(subscriptionAttemptCommandPort.persist(attempt)).willReturn(123L);

            // when
            SubscriptionAttempt persisted = facade.saveAttempt(attempt);

            // then
            assertThat(persisted.idValue()).isEqualTo(123L);
        }
    }
}
