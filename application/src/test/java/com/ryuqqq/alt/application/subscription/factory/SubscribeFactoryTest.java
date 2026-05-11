package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommandFixture;
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
@DisplayName("SubscribeFactory — 구독 번들 빌드 단위 테스트")
class SubscribeFactoryTest {

    @Mock TimeProvider timeProvider;

    @InjectMocks SubscribeFactory factory;

    private static final Instant FIXED_NOW = Instant.parse("2026-05-10T12:00:00Z");

    @Test
    @DisplayName("PREMIUM 타겟으로 빌드 시 draft member (id=null, status=NONE) + null channel + COMMITTED attempt 가 만들어진다")
    void shouldBuildBundleForPremiumTarget() {
        // given
        SubscribeCommand command = SubscribeCommandFixture.subscribePremium();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        SubscribeBundle bundle = factory.createBundle(command);

        // then
        assertThat(bundle.member().id().isNew()).isTrue();
        assertThat(bundle.member().phoneNumber()).isEqualTo(command.phoneNumber());
        assertThat(bundle.member().status()).isEqualTo(SubscriptionStatus.NONE);

        assertThat(bundle.channel()).isNull();

        SubscriptionAttempt attempt = bundle.attempt();
        assertThat(attempt.kind()).isEqualTo(AttemptKind.SUBSCRIBE);
        assertThat(attempt.status()).isEqualTo(AttemptStatus.COMMITTED);
        assertThat(attempt.fromStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(attempt.toStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        assertThat(attempt.requestedAt()).isEqualTo(FIXED_NOW);
        assertThat(attempt.completedAt()).isEqualTo(FIXED_NOW);
        assertThat(attempt.idempotencyKey()).isEqualTo(command.idempotencyKey());
        assertThat(attempt.channelId()).isEqualTo(command.channelId());
    }

    @Test
    @DisplayName("BASIC 타겟으로 빌드 시 attempt.toStatus = BASIC")
    void shouldBuildBundleForBasicTarget() {
        // given
        SubscribeCommand command = SubscribeCommandFixture.subscribeBasic();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        SubscribeBundle bundle = factory.createBundle(command);

        // then
        assertThat(bundle.attempt().toStatus()).isEqualTo(SubscriptionStatus.BASIC);
    }

    @Test
    @DisplayName("NONE 타겟(registration-only)으로 빌드 시 attempt.toStatus = NONE 이고 isRegistrationOnly 분기에 활용된다")
    void shouldBuildBundleForRegistrationOnly() {
        // given
        SubscribeCommand command = SubscribeCommandFixture.registrationOnly();
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        SubscribeBundle bundle = factory.createBundle(command);

        // then
        assertThat(bundle.attempt().toStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(bundle.isRegistrationOnly()).isTrue();
    }

    @Test
    @DisplayName("idempotencyKey 가 null 이어도 attempt 에 그대로 보존된다 (점진적 도입 정책)")
    void shouldPreserveNullIdempotencyKey() {
        // given
        SubscribeCommand command = SubscribeCommandFixture.of(
            SubscribeCommandFixture.DEFAULT_PHONE,
            SubscribeCommandFixture.DEFAULT_CHANNEL_ID,
            SubscriptionStatus.PREMIUM,
            null
        );
        given(timeProvider.now()).willReturn(FIXED_NOW);

        // when
        SubscribeBundle bundle = factory.createBundle(command);

        // then
        assertThat(bundle.attempt().idempotencyKey()).isNull();
    }
}
