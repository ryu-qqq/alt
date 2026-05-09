package com.ryuqqq.alt.domain.error;

public class InvalidSubscribeTransitionException extends SubscriptionException {

    public InvalidSubscribeTransitionException(String detail) {
        super(SubscriptionErrorCode.INVALID_SUBSCRIBE_TRANSITION, detail);
    }
}
