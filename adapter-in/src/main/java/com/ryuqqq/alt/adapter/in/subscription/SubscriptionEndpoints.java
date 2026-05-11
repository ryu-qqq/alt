package com.ryuqqq.alt.adapter.in.subscription;

/**
 * 구독 API URL 상수.
 */
public final class SubscriptionEndpoints {

    public static final String BASE = "/api/v1/subscriptions";
    public static final String SUBSCRIBE = BASE + "/subscribe";
    public static final String UNSUBSCRIBE = BASE + "/unsubscribe";
    public static final String HISTORY = BASE + "/history";

    private SubscriptionEndpoints() {
    }
}
