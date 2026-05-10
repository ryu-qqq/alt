package com.ryuqqq.alt.application.subscription.dto.llm;

/**
 * LLM 요약 호출 결과. csrng 와 동일한 sealed 패턴.
 * 실패 시 Unavailable 로 반환되며, 호출자(QuerySubscriptionHistoryService) 는 graceful degradation 한다
 * (이력 자체는 반환하고 summary 만 비움).
 */
public sealed interface LlmSummaryOutcome {

    record Success(String summary) implements LlmSummaryOutcome { }

    record Unavailable(String reason) implements LlmSummaryOutcome { }
}
