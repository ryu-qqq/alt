package com.ryuqqq.alt.domain.subscription;

/**
 * SubscriptionAttempt Aggregate Root의 ID VO (DOM-ID-001).
 */
public record AttemptId(Long value) {

    public AttemptId {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("AttemptId must be positive when present: " + value);
        }
    }

    public static AttemptId of(Long value) {
        return new AttemptId(value);
    }

    public static AttemptId forNew() {
        return new AttemptId(null);
    }

    public boolean isNew() {
        return value == null;
    }
}
