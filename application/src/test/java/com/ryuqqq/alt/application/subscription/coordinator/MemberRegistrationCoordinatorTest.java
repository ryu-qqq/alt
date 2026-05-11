package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberRegistrationCoordinator — 회원 조회/등록 코디네이터 단위 테스트")
class MemberRegistrationCoordinatorTest {

    @Mock MemberQueryPort memberQueryPort;
    @Mock MemberCommandPort memberCommandPort;

    @InjectMocks MemberRegistrationCoordinator coordinator;

    @Nested
    @DisplayName("기존 회원 hit")
    class ExistingMember {

        @Test
        @DisplayName("phoneNumber 로 기존 회원이 조회되면 그대로 반환하고 commandPort.persist 는 호출하지 않는다")
        void shouldReturnExistingMemberAndSkipPersist() {
            // given
            Member draft = MemberFixture.newMember();
            Member existing = MemberFixture.basicMember();
            given(memberQueryPort.findByPhoneNumber(draft.phoneNumber())).willReturn(Optional.of(existing));

            // when
            Member result = coordinator.findOrRegister(draft);

            // then
            assertThat(result).isSameAs(existing);
            verify(memberQueryPort, times(1)).findByPhoneNumber(draft.phoneNumber());
            verifyNoInteractions(memberCommandPort);
        }
    }

    @Nested
    @DisplayName("신규 회원 등록")
    class NewMember {

        @Test
        @DisplayName("phoneNumber 로 회원이 없으면 commandPort.persist 후 채번된 id 가 주입된 Member 를 반환한다")
        void shouldPersistAndReturnMemberWithAssignedId() {
            // given
            Member draft = MemberFixture.newMember();
            assertThat(draft.id().isNew()).isTrue(); // 가정 검증

            given(memberQueryPort.findByPhoneNumber(draft.phoneNumber())).willReturn(Optional.empty());
            given(memberCommandPort.persist(draft)).willReturn(42L);

            // when
            Member result = coordinator.findOrRegister(draft);

            // then
            assertThat(result.id()).isEqualTo(MemberId.of(42L));
            assertThat(result.phoneNumber()).isEqualTo(draft.phoneNumber());
            assertThat(result.status()).isEqualTo(SubscriptionStatus.NONE);
            verify(memberCommandPort, times(1)).persist(draft);
        }

        @Test
        @DisplayName("신규 등록 시 원본 draft 의 phoneNumber 와 status 가 보존된다")
        void shouldPreserveDraftPhoneNumberAndStatusAfterPersist() {
            // given — BASIC 상태로 미리 설정된 draft (실제 흐름에선 NONE 이 일반적이지만 보존 검증 목적)
            Member draft = MemberFixture.newMemberWithStatus(SubscriptionStatus.BASIC);
            given(memberQueryPort.findByPhoneNumber(draft.phoneNumber())).willReturn(Optional.empty());
            given(memberCommandPort.persist(draft)).willReturn(7L);

            // when
            Member result = coordinator.findOrRegister(draft);

            // then
            assertThat(result.id().value()).isEqualTo(7L);
            assertThat(result.phoneNumber()).isEqualTo(draft.phoneNumber());
            assertThat(result.status()).isEqualTo(SubscriptionStatus.BASIC);
        }

        @Test
        @DisplayName("findByPhoneNumber 가 먼저 호출되고 commandPort.persist 가 그 후 호출되어야 한다")
        void shouldQueryBeforePersistOnNewMember() {
            // given
            Member draft = MemberFixture.newMember();
            PhoneNumber phone = draft.phoneNumber();
            given(memberQueryPort.findByPhoneNumber(phone)).willReturn(Optional.empty());
            given(memberCommandPort.persist(draft)).willReturn(1L);

            // when
            coordinator.findOrRegister(draft);

            // then
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(memberQueryPort, memberCommandPort);
            order.verify(memberQueryPort).findByPhoneNumber(phone);
            order.verify(memberCommandPort).persist(draft);
        }

        @Test
        @DisplayName("기존 회원이 있을 때는 commandPort.persist 가 절대 호출되지 않는다 (verify never)")
        void shouldNeverCallPersistWhenExisting() {
            // given
            Member draft = MemberFixture.newMember();
            Member existing = MemberFixture.reconstitutedMember();
            given(memberQueryPort.findByPhoneNumber(draft.phoneNumber())).willReturn(Optional.of(existing));

            // when
            coordinator.findOrRegister(draft);

            // then
            verify(memberCommandPort, never()).persist(org.mockito.ArgumentMatchers.any());
        }
    }
}
