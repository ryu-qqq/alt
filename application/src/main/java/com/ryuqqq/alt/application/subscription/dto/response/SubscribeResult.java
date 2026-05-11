package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

/**
 * 구독 요청 결과.
 *
 * - attempt 가 만들어진 케이스: from(attempt, currentStatus) — 일반 흐름
 * - 회원만 등록되고 종료 (target=NONE): registrationOnly(currentStatus) — attemptId/status null
 */
public record SubscribeResult(
    Long attemptId,
    AttemptStatus status,
    SubscriptionStatus currentStatus,
    String failureReason
) {

    public static SubscribeResult from(SubscriptionAttempt attempt, SubscriptionStatus memberCurrentStatus) {
        return new SubscribeResult(
            attempt.id().value(),
            attempt.status(),
            memberCurrentStatus,
            attempt.failureReason() != null ? attempt.failureReason().name() : null
        );
    }

    public static SubscribeResult registrationOnly(SubscriptionStatus currentStatus) {
        return new SubscribeResult(null, null, currentStatus, null);
    }
}
