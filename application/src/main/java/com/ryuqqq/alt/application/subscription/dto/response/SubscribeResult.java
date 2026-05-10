package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

/**
 * 구독 시도 결과. attempt 의 status 가 그대로 노출되어 클라이언트가
 * COMMITTED / ROLLED_BACK / FAILED 를 구분할 수 있다.
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
}
