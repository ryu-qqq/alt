package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UnsubscribeResult — 해지 응답 DTO 단위 테스트")
class UnsubscribeResultTest {

    @Test
    @DisplayName("COMMITTED attempt → COMMITTED status + currentStatus 매핑 + failureReason=null")
    void shouldMapCommittedAttempt() {
        // given
        SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedCommitted(11L);

        // when
        UnsubscribeResult result = UnsubscribeResult.from(attempt, SubscriptionStatus.NONE);

        // then
        assertThat(result.attemptId()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo(AttemptStatus.COMMITTED);
        assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("ROLLED_BACK attempt → failureReason=EXTERNAL_REJECTED 이름 매핑")
    void shouldMapRolledBackAttempt() {
        // given
        SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedRolledBack(20L);

        // when
        UnsubscribeResult result = UnsubscribeResult.from(attempt, SubscriptionStatus.PREMIUM);

        // then
        assertThat(result.attemptId()).isEqualTo(20L);
        assertThat(result.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
        assertThat(result.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED.name());
    }

    @Test
    @DisplayName("FAILED attempt → failureReason 이 attempt 의 reason 이름과 일치한다")
    void shouldMapFailedAttempt() {
        // given
        SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedFailed(30L, AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN);

        // when
        UnsubscribeResult result = UnsubscribeResult.from(attempt, SubscriptionStatus.PREMIUM);

        // then
        assertThat(result.attemptId()).isEqualTo(30L);
        assertThat(result.status()).isEqualTo(AttemptStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN.name());
    }
}
