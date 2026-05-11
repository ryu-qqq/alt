package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LlmSummaryClientManager — LLM 요약 호출 래퍼 단위 테스트")
class LlmSummaryClientManagerTest {

    @Mock LlmSummaryClient llmSummaryClient;

    @InjectMocks LlmSummaryClientManager manager;

    @Test
    @DisplayName("success outcome 을 그대로 위임하여 반환한다")
    void shouldDelegateSuccessOutcome() {
        // given
        SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
        LlmSummaryOutcome expected = LlmSummaryOutcome.success("최신 요약");
        given(llmSummaryClient.summarize(bundle)).willReturn(expected);

        // when
        LlmSummaryOutcome result = manager.summarize(bundle);

        // then
        assertThat(result).isSameAs(expected);
        verify(llmSummaryClient, times(1)).summarize(bundle);
    }

    @Test
    @DisplayName("unavailable outcome 을 그대로 위임하여 반환한다")
    void shouldDelegateUnavailableOutcome() {
        // given
        SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
        LlmSummaryOutcome expected = LlmSummaryOutcome.unavailable("LLM_TIMEOUT");
        given(llmSummaryClient.summarize(bundle)).willReturn(expected);

        // when
        LlmSummaryOutcome result = manager.summarize(bundle);

        // then
        assertThat(result).isSameAs(expected);
    }
}
