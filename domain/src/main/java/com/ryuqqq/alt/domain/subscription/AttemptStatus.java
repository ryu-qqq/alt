package com.ryuqqq.alt.domain.subscription;

public enum AttemptStatus {

    PENDING("대기"),
    COMMITTED("커밋"),
    ROLLED_BACK("롤백"),
    FAILED("실패");

    private final String displayName;

    AttemptStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this != PENDING;
    }
}
