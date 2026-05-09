package com.ryuqqq.alt.domain.subscription;

public enum AttemptFailureReason {

    CSRNG_REJECTED("csrng 응답 random=0 — 의도된 롤백"),
    CSRNG_UNAVAILABLE("csrng 외부 API 장애 (timeout / 5xx / circuit open)");

    private final String displayName;

    AttemptFailureReason(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
