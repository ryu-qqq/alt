package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;

/**
 * LLM 자연어 요약 Client Port.
 * 어댑터 구현(client-llm)은 Bundle 을 자체 매퍼로 LLM API 입력 형식으로 변환해 호출한다.
 * 모든 실패 케이스는 LlmSummaryUnavailable 로 변환해 반환한다.
 */
public interface LlmSummaryClient {

    LlmSummaryOutcome summarize(SubscriptionHistoryReadBundle bundle);
}
