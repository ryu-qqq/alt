package com.ryuqqq.alt.domain.subscription;

/**
 * 시도의 종결 상태. PENDING 중간 상태는 두지 않는다 (외부 응답 직접 반영 정책, ADR-0006).
 * 모든 SubscriptionAttempt 는 생성 시점에 이미 종결 상태를 가진다.
 */
public enum AttemptStatus {

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
}
