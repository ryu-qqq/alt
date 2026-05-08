package com.ryuqqq.alt.domain.channel;

public enum ChannelType {

    SUBSCRIBE_ONLY(true, false),
    UNSUBSCRIBE_ONLY(false, true),
    BOTH(true, true);

    private final boolean subscribable;
    private final boolean unsubscribable;

    ChannelType(boolean subscribable, boolean unsubscribable) {
        this.subscribable = subscribable;
        this.unsubscribable = unsubscribable;
    }

    public boolean canSubscribe() {
        return subscribable;
    }

    public boolean canUnsubscribe() {
        return unsubscribable;
    }
}
