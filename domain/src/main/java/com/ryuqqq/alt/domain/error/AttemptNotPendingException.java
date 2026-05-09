package com.ryuqqq.alt.domain.error;

public class AttemptNotPendingException extends SubscriptionException {

    public AttemptNotPendingException(String detail) {
        super(SubscriptionErrorCode.ATTEMPT_NOT_PENDING, detail);
    }
}
