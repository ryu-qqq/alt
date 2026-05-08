package com.ryuqqq.alt.domain.subscription;

public record AttemptId(long value) {

    public AttemptId {
        if (value <= 0) {
            throw new IllegalArgumentException("AttemptId must be positive: " + value);
        }
    }
}
