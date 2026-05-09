package com.ryuqqq.alt.domain.error;

public class ChannelUnsubscribeNotAllowedException extends SubscriptionException {

    public ChannelUnsubscribeNotAllowedException(String detail) {
        super(SubscriptionErrorCode.CHANNEL_UNSUBSCRIBE_NOT_ALLOWED, detail);
    }
}
