package com.ryuqqq.alt.application.subscription.dto.response;

import java.util.List;

/**
 * 이력 조회 응답. summary 는 LLM 실패 시 null 로 반환 (graceful degradation).
 */
public record QuerySubscriptionHistoryResult(
    List<SubscriptionHistoryItemView> history,
    String summary
) {

    public QuerySubscriptionHistoryResult {
        history = List.copyOf(history);
    }

    public static QuerySubscriptionHistoryResult of(List<SubscriptionHistoryItemView> history, String summary) {
        return new QuerySubscriptionHistoryResult(history, summary);
    }

    public static QuerySubscriptionHistoryResult withoutSummary(List<SubscriptionHistoryItemView> history) {
        return new QuerySubscriptionHistoryResult(history, null);
    }
}
