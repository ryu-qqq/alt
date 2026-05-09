package com.ryuqqq.alt.domain.error;

public class ChannelNotFoundException extends SubscriptionException {

    public ChannelNotFoundException() {
        super(SubscriptionErrorCode.CHANNEL_NOT_FOUND);
    }

    public ChannelNotFoundException(String detail) {
        super(SubscriptionErrorCode.CHANNEL_NOT_FOUND, detail);
    }
}
