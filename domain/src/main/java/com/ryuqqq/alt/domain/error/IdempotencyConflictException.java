package com.ryuqqq.alt.domain.error;

public class IdempotencyConflictException extends SubscriptionException {

    public IdempotencyConflictException() {
        super(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT);
    }

    public IdempotencyConflictException(String idempotencyKey) {
        super(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT, "key=" + idempotencyKey);
    }
}
