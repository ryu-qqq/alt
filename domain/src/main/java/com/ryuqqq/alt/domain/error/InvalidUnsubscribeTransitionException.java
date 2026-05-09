package com.ryuqqq.alt.domain.error;

public class InvalidUnsubscribeTransitionException extends SubscriptionException {

    public InvalidUnsubscribeTransitionException(String detail) {
        super(SubscriptionErrorCode.INVALID_UNSUBSCRIBE_TRANSITION, detail);
    }
}
