package com.ryuqqq.alt.adapter.in.subscription.dto.response;

import java.util.List;

/**
 * 구독 이력 조회 응답.
 *
 * - history : COMMITTED 이력 목록 (어댑터 정렬 그대로 — 최신순)
 * - summary : LLM 자연어 요약. 이력 0건 또는 LLM Unavailable 시 null
 */
public record QuerySubscriptionHistoryApiResponse(
    List<SubscriptionHistoryItemApiView> history,
    String summary
) {

    public QuerySubscriptionHistoryApiResponse {
        history = List.copyOf(history);
    }
}
