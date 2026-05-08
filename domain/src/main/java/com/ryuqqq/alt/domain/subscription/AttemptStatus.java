package com.ryuqqq.alt.domain.subscription;

public enum AttemptStatus {

    PENDING,
    COMMITTED,
    ROLLED_BACK,
    FAILED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
