package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;

import java.time.Instant;

/**
 * 사용자 노출용 이력 한 줄 (COMMITTED 만 노출).
 * Adapter-in 의 응답 DTO 와 분리되어 있어 외부 계약과 응용 표현을 분리한다.
 */
public record SubscriptionHistoryItemView(
    Long attemptId,
    Long channelId,
    String channelName,
    AttemptKind kind,
    SubscriptionStatus fromStatus,
    SubscriptionStatus toStatus,
    Instant occurredAt
) {
}
