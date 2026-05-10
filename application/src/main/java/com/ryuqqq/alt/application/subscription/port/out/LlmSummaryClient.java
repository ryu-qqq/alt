package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.application.subscription.dto.llm.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.llm.SubscriptionHistorySummaryRequest;

/**
 * LLM 자연어 요약 Client Port.
 * 어댑터 구현(client-llm)은 Anthropic Claude API + Resilience4j 를 적용한다.
 * 모든 실패 케이스를 LlmSummaryOutcome.Unavailable 로 변환해 반환한다.
 */
public interface LlmSummaryClient {

    LlmSummaryOutcome summarize(SubscriptionHistorySummaryRequest request);
}
