package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.manager.HistorySummaryCommandManager;
import com.ryuqqq.alt.application.subscription.manager.LlmSummaryClientManager;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("HistorySummaryRefreshFacade — LLM 호출 + 결과 영속화 단위 테스트")
class HistorySummaryRefreshFacadeTest {

    @Mock LlmSummaryClientManager llmSummaryClientManager;
    @Mock HistorySummaryCommandManager historySummaryCommandManager;

    @InjectMocks HistorySummaryRefreshFacade facade;

    @Nested
    @DisplayName("LLM 성공 (success)")
    class WhenSuccess {

        @Test
        @DisplayName("isAvailable=true 면 HistorySummary 로 commandManager.persist 를 호출하고 outcome 을 그대로 반환한다")
        void shouldPersistAndReturnSuccessOutcome() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome success = LlmSummaryOutcome.success("최신 요약 한 줄");
            given(llmSummaryClientManager.summarize(bundle)).willReturn(success);

            // when
            LlmSummaryOutcome result = facade.refresh(bundle);

            // then
            assertThat(result).isSameAs(success);

            ArgumentCaptor<HistorySummary> captor = ArgumentCaptor.forClass(HistorySummary.class);
            verify(historySummaryCommandManager, times(1)).persist(captor.capture());
            HistorySummary persisted = captor.getValue();
            assertThat(persisted.memberId()).isEqualTo(bundle.memberId());
            assertThat(persisted.fingerprint()).isEqualTo(bundle.fingerprint());
            assertThat(persisted.summary()).isEqualTo("최신 요약 한 줄");
        }

        @Test
        @DisplayName("LLM 호출 후 persist 가 호출되는 순서를 보장한다")
        void shouldCallLlmThenPersist() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            given(llmSummaryClientManager.summarize(bundle)).willReturn(LlmSummaryOutcome.success("요약"));

            // when
            facade.refresh(bundle);

            // then
            InOrder order = inOrder(llmSummaryClientManager, historySummaryCommandManager);
            order.verify(llmSummaryClientManager).summarize(bundle);
            order.verify(historySummaryCommandManager).persist(any(HistorySummary.class));
        }
    }

    @Nested
    @DisplayName("LLM 사용 불가 (unavailable)")
    class WhenUnavailable {

        @Test
        @DisplayName("isAvailable=false 면 commandManager.persist 가 호출되지 않고 outcome 만 그대로 반환된다")
        void shouldSkipPersistAndReturnUnavailable() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome unavailable = LlmSummaryOutcome.unavailable("LLM_TIMEOUT");
            given(llmSummaryClientManager.summarize(bundle)).willReturn(unavailable);

            // when
            LlmSummaryOutcome result = facade.refresh(bundle);

            // then
            assertThat(result).isSameAs(result); // identity 유지
            assertThat(result).isSameAs(unavailable);
            verify(historySummaryCommandManager, never()).persist(any());
        }

        @Test
        @DisplayName("empty outcome (호출 자체가 불필요) 도 persist 를 호출하지 않는다")
        void shouldSkipPersistWhenEmpty() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome empty = LlmSummaryOutcome.empty();
            given(llmSummaryClientManager.summarize(bundle)).willReturn(empty);

            // when
            LlmSummaryOutcome result = facade.refresh(bundle);

            // then
            assertThat(result).isSameAs(empty);
            verify(historySummaryCommandManager, never()).persist(any());
        }
    }
}
