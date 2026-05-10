package com.ryuqqq.alt.application.subscription.dto.llm;

import java.time.Instant;
import java.util.List;

/**
 * LLM 요약 호출용 Application DTO. 도메인 객체(SubscriptionHistory) 를 어댑터에 직접 노출하지 않는다 (APP-PRT-003).
 *
 * Service 가 SubscriptionHistory + Member + Channel 정보를 통합해 평탄화된 표현으로 변환하여 전달한다.
 */
public record SubscriptionHistorySummaryRequest(
    String memberPhoneNumberValue,
    List<HistoryItem> items
) {

    public SubscriptionHistorySummaryRequest {
        items = List.copyOf(items);
    }

    public record HistoryItem(
        String channelName,
        String fromStatusDisplayName,
        String toStatusDisplayName,
        String kindDisplayName,
        Instant occurredAt
    ) {
    }
}
