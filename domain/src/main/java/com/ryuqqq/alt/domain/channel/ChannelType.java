package com.ryuqqq.alt.domain.channel;

public enum ChannelType {

    SUBSCRIBE_ONLY("구독 전용", true, false),
    UNSUBSCRIBE_ONLY("해지 전용", false, true),
    BOTH("구독/해지", true, true);

    private final String displayName;
    private final boolean subscribable;
    private final boolean unsubscribable;

    ChannelType(String displayName, boolean subscribable, boolean unsubscribable) {
        this.displayName = displayName;
        this.subscribable = subscribable;
        this.unsubscribable = unsubscribable;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canSubscribe() {
        return subscribable;
    }

    public boolean canUnsubscribe() {
        return unsubscribable;
    }
}
