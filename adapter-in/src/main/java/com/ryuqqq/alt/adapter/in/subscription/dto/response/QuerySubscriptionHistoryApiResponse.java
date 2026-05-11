package com.ryuqqq.alt.adapter.in.subscription.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 구독 이력 조회 응답.
 *
 * - history             : COMMITTED 이력 목록 (어댑터 정렬 그대로 — 최신순)
 * - summary             : LLM 자연어 요약. 이력 0건 또는 LLM 실패 + 폴백 없음 시 null.
 * - summaryGeneratedAt  : 요약 생성 시각. summary 가 null 이면 null.
 * - summaryStale        : true 면 LLM 재호출 실패로 영속체의 옛 요약을 폴백 반환한 케이스.
 *                         클라이언트가 "최근 요약이 아닐 수 있음" 안내 가능.
 */
public record QuerySubscriptionHistoryApiResponse(
    List<SubscriptionHistoryItemApiView> history,
    String summary,
    Instant summaryGeneratedAt,
    boolean summaryStale
) {

    public QuerySubscriptionHistoryApiResponse {
        history = List.copyOf(history);
    }
}
