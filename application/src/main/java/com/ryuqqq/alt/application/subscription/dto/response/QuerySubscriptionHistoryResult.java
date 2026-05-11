package com.ryuqqq.alt.application.subscription.dto.response;

import java.util.List;

/**
 * 이력 조회 응답.
 *
 * - history : 구조화된 이력 (DB 에서 직접, 어댑터 정렬 보존). 예측 가능한 스키마.
 * - summary : LLM 이 생성한 현재 상태 한 줄. 실패 시 null.
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
