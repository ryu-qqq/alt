package com.ryuqqq.alt.domain.subscription;

public enum AttemptKind {

    SUBSCRIBE("구독"),
    UNSUBSCRIBE("해지");

    private final String displayName;

    AttemptKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
