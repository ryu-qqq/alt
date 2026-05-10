package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.llm.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.llm.SubscriptionHistorySummaryRequest;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import org.springframework.stereotype.Component;

@Component
public class LlmSummaryClientManager {

    private final LlmSummaryClient llmSummaryClient;

    public LlmSummaryClientManager(LlmSummaryClient llmSummaryClient) {
        this.llmSummaryClient = llmSummaryClient;
    }

    public LlmSummaryOutcome summarize(SubscriptionHistorySummaryRequest request) {
        return llmSummaryClient.summarize(request);
    }
}
