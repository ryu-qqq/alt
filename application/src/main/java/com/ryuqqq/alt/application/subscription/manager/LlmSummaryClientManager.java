package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import org.springframework.stereotype.Component;

/**
 * LLM 요약 호출 래퍼.
 * Bundle → LLM 입력 변환은 어댑터의 매퍼가 담당한다 (APP-PRT-003 정신 유지 — 어댑터가 외부 표현으로 매핑).
 */
@Component
public class LlmSummaryClientManager {

    private final LlmSummaryClient llmSummaryClient;

    public LlmSummaryClientManager(LlmSummaryClient llmSummaryClient) {
        this.llmSummaryClient = llmSummaryClient;
    }

    public LlmSummaryOutcome summarize(SubscriptionHistoryReadBundle bundle) {
        return llmSummaryClient.summarize(bundle);
    }
}
