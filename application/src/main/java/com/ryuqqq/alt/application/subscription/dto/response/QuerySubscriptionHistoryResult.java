package com.ryuqqq.alt.application.subscription.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 이력 조회 응답.
 *
 * - history             : 구조화된 이력 (DB 에서 직접, 어댑터 정렬 보존). 예측 가능한 스키마.
 * - summary             : LLM 자연어 요약. 이력 0건 또는 LLM 실패 + 폴백 없음 시 null.
 * - summaryGeneratedAt  : 요약이 생성된 시각. summary 가 null 이면 null.
 * - summaryStale        : true 면 LLM 재호출 실패로 영속체의 옛 요약을 폴백 반환한 케이스.
 *                         클라이언트가 "최근 요약이 아닐 수 있음" 안내 가능.
 */
public record QuerySubscriptionHistoryResult(
    List<SubscriptionHistoryItemView> history,
    String summary,
    Instant summaryGeneratedAt,
    boolean summaryStale
) {

    public QuerySubscriptionHistoryResult {
        history = List.copyOf(history);
    }

    public static QuerySubscriptionHistoryResult of(
        List<SubscriptionHistoryItemView> history,
        LlmSummaryOutcome outcome
    ) {
        return new QuerySubscriptionHistoryResult(
            history,
            outcome.summary(),
            outcome.generatedAt(),
            outcome.stale()
        );
    }

    /**
     * 테스트 backward compat — summary 만 검증하는 기존 테스트 보존용.
     * 운영 코드는 of(List, LlmSummaryOutcome) 를 사용한다.
     */
    public static QuerySubscriptionHistoryResult of(
        List<SubscriptionHistoryItemView> history,
        String summary
    ) {
        return new QuerySubscriptionHistoryResult(history, summary, null, false);
    }

    public static QuerySubscriptionHistoryResult withoutSummary(List<SubscriptionHistoryItemView> history) {
        return new QuerySubscriptionHistoryResult(history, null, null, false);
    }
}
