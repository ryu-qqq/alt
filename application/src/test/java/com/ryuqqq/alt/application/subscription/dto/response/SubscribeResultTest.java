package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscribeResult — 구독 응답 DTO 단위 테스트")
class SubscribeResultTest {

    @Nested
    @DisplayName("from — attempt 기반 빌드")
    class FromAttempt {

        @Test
        @DisplayName("COMMITTED attempt 는 attemptId / status / currentStatus 매핑되고 failureReason=null")
        void shouldMapCommittedAttempt() {
            // given
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedCommitted(123L);

            // when
            SubscribeResult result = SubscribeResult.from(attempt, SubscriptionStatus.PREMIUM);

            // then
            assertThat(result.attemptId()).isEqualTo(123L);
            assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(result.failureReason()).isNull();
        }

        @Test
        @DisplayName("ROLLED_BACK attempt 는 failureReason 이름이 EXTERNAL_REJECTED 로 매핑된다")
        void shouldMapRolledBackAttempt() {
            // given
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedRolledBack(50L);

            // when
            SubscribeResult result = SubscribeResult.from(attempt, SubscriptionStatus.NONE);

            // then
            assertThat(result.attemptId()).isEqualTo(50L);
            assertThat(result.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED.name());
        }

        @Test
        @DisplayName("FAILED attempt 는 failureReason 이름이 매핑된다")
        void shouldMapFailedAttempt() {
            // given
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedFailed(99L, AttemptFailureReason.EXTERNAL_TIMEOUT);

            // when
            SubscribeResult result = SubscribeResult.from(attempt, SubscriptionStatus.NONE);

            // then
            assertThat(result.attemptId()).isEqualTo(99L);
            assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT.name());
        }
    }

    @Nested
    @DisplayName("registrationOnly")
    class RegistrationOnly {

        @Test
        @DisplayName("attemptId/status/failureReason 은 null, currentStatus 만 채운다")
        void shouldOnlySetCurrentStatus() {
            // when
            SubscribeResult result = SubscribeResult.registrationOnly(SubscriptionStatus.NONE);

            // then
            assertThat(result.attemptId()).isNull();
            assertThat(result.status()).isNull();
            assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(result.failureReason()).isNull();
        }
    }
}
