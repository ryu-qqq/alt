package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberReadManager — 회원 조회 매니저 단위 테스트")
class MemberReadManagerTest {

    @Mock MemberQueryPort memberQueryPort;

    @InjectMocks MemberReadManager manager;

    private static final PhoneNumber PHONE = PhoneNumber.of("01099998888");

    @Nested
    @DisplayName("findByPhoneNumber")
    class FindByPhoneNumber {

        @Test
        @DisplayName("존재하면 Optional<Member> 를 그대로 반환한다")
        void shouldReturnPresentWhenExists() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.of(member));

            // when
            Optional<Member> result = manager.findByPhoneNumber(PHONE);

            // then
            assertThat(result).contains(member);
        }

        @Test
        @DisplayName("없으면 Optional.empty() 를 반환한다 (예외 미발생)")
        void shouldReturnEmptyWhenAbsent() {
            // given
            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.empty());

            // when
            Optional<Member> result = manager.findByPhoneNumber(PHONE);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByPhoneNumber")
    class GetByPhoneNumber {

        @Test
        @DisplayName("존재하면 Member 를 반환한다")
        void shouldReturnMemberWhenExists() {
            // given
            Member member = MemberFixture.basicMember();
            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.of(member));

            // when
            Member result = manager.getByPhoneNumber(PHONE);

            // then
            assertThat(result).isSameAs(member);
        }

        @Test
        @DisplayName("없으면 MemberNotFoundException 을 던지며 메시지에 phoneNumber value 가 포함된다")
        void shouldThrowMemberNotFoundWhenAbsent() {
            // given
            given(memberQueryPort.findByPhoneNumber(PHONE)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> manager.getByPhoneNumber(PHONE))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining(PHONE.value());
        }
    }
}
