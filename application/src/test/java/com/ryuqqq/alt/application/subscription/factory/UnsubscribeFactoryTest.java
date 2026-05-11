package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommandFixture;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnsubscribeFactory — 해지 번들 빌드 단위 테스트")
class UnsubscribeFactoryTest {

    @Mock TimeProvider timeProvider;

    @InjectMocks UnsubscribeFactory factory;

    private static final Instant FIXED_NOW = Instant.parse("2026-05-10T15:30:00Z");

    @Test
    @DisplayName("PREMIUM 회원의 NONE 해지 빌드 시 fromStatus=PREMIUM, toStatus=NONE 의 COMMITTED attempt + persisted member + null channel 가 만들어진다")
    void shouldBuildBundleForPremiumToNone() {
        // given
        Member member = MemberFixture.premiumMember();
        UnsubscribeCommand command = UnsubscribeCommandFixture.unsubscribeToNone();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        UnsubscribeBundle bundle = factory.createBundle(member, command);

        // then
        // 영속 member 가 그대로 보존됨 (해지는 신규 등록 경로 없음)
        assertThat(bundle.member()).isSameAs(member);
        assertThat(bundle.channel()).isNull();

        SubscriptionAttempt attempt = bundle.attempt();
        assertThat(attempt.kind()).isEqualTo(AttemptKind.UNSUBSCRIBE);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.COMMITTED);
        assertThat(attempt.fromStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        assertThat(attempt.toStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(attempt.requestedAt()).isEqualTo(FIXED_NOW);
        assertThat(attempt.completedAt()).isEqualTo(FIXED_NOW);
        assertThat(attempt.idempotencyKey()).isEqualTo(command.idempotencyKey());
        assertThat(attempt.channelId()).isEqualTo(command.channelId());
        assertThat(attempt.memberId()).isEqualTo(member.id());
    }

    @Test
    @DisplayName("BASIC 회원의 NONE 해지 빌드 시 fromStatus=BASIC, toStatus=NONE")
    void shouldBuildBundleForBasicToNone() {
        // given
        Member member = MemberFixture.basicMember();
        UnsubscribeCommand command = UnsubscribeCommandFixture.unsubscribeToNone();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        UnsubscribeBundle bundle = factory.createBundle(member, command);

        // then
        assertThat(bundle.attempt().fromStatus()).isEqualTo(SubscriptionStatus.BASIC);
        assertThat(bundle.attempt().toStatus()).isEqualTo(SubscriptionStatus.NONE);
    }

    @Test
    @DisplayName("PREMIUM 회원의 BASIC 해지 빌드 시 toStatus=BASIC 으로 attempt 가 만들어진다")
    void shouldBuildBundleForPremiumToBasic() {
        // given
        Member member = MemberFixture.premiumMember();
        UnsubscribeCommand command = UnsubscribeCommandFixture.unsubscribeToBasic();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        UnsubscribeBundle bundle = factory.createBundle(member, command);

        // then
        assertThat(bundle.attempt().fromStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        assertThat(bundle.attempt().toStatus()).isEqualTo(SubscriptionStatus.BASIC);
    }
}
