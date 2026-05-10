package com.ryuqqq.alt.application.subscription.dto.response;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

public record UnsubscribeResult(
    Long attemptId,
    AttemptStatus status,
    SubscriptionStatus currentStatus,
    String failureReason
) {

    public static UnsubscribeResult from(SubscriptionAttempt attempt, SubscriptionStatus memberCurrentStatus) {
        return new UnsubscribeResult(
            attempt.id().value(),
            attempt.status(),
            memberCurrentStatus,
            attempt.failureReason() != null ? attempt.failureReason().name() : null
        );
    }
}
