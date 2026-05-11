package com.ryuqqq.alt.adapter.in.subscription.dto.response;

import java.time.Instant;

/**
 * 이력 단건 응답 DTO. COMMITTED 시도만 노출된다.
 *
 * - kind / fromStatus / toStatus 는 enum name (SUBSCRIBE / UNSUBSCRIBE / NONE / BASIC / PREMIUM)
 * - occurredAt 은 ISO-8601 (Jackson 기본)
 */
public record SubscriptionHistoryItemApiView(
    Long attemptId,
    Long channelId,
    String channelName,
    String kind,
    String fromStatus,
    String toStatus,
    Instant occurredAt
) {
}
