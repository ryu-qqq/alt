package com.ryuqqq.alt.adapter.out.client.llm.adapter;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NoOpLlmClient 단위 테스트.
 * api-key 미설정 환경에서 부팅 보장용 — 항상 unavailable 반환.
 */
class NoOpLlmClientTest {

    @Test
    @DisplayName("singleCommitted bundle 을 받아도 항상 unavailable 을 반환한다")
    void shouldAlwaysReturnUnavailableWithCommittedBundle() {
        // given
        NoOpLlmClient client = new NoOpLlmClient();

        // when
        LlmSummaryOutcome outcome = client.summarize(
            SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary()
        );

        // then
        assertThat(outcome.isAvailable()).isFalse();
        assertThat(outcome.unavailableReason()).contains("api key not configured");
    }

    @Test
    @DisplayName("empty bundle 을 받아도 항상 unavailable 을 반환한다")
    void shouldAlwaysReturnUnavailableWithEmptyBundle() {
        // given
        NoOpLlmClient client = new NoOpLlmClient();

        // when
        LlmSummaryOutcome outcome = client.summarize(SubscriptionHistoryReadBundleFixture.empty());

        // then
        assertThat(outcome.isAvailable()).isFalse();
        assertThat(outcome.unavailableReason()).contains("api key not configured");
    }
}
