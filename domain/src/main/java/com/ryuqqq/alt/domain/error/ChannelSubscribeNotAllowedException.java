package com.ryuqqq.alt.domain.error;

public class ChannelSubscribeNotAllowedException extends SubscriptionException {

    public ChannelSubscribeNotAllowedException(String detail) {
        super(SubscriptionErrorCode.CHANNEL_SUBSCRIBE_NOT_ALLOWED, detail);
    }
}
