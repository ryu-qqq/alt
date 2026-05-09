package com.ryuqqq.alt.domain.member;

/**
 * 회원 구독 상태와 전이 규칙. 전이 규칙은 enum 자체에 캡슐화 (DOM-ENUM-001 변형).
 *
 * 구독 전이: NONE → {BASIC, PREMIUM}, BASIC → PREMIUM
 * 해지 전이: PREMIUM → {BASIC, NONE}, BASIC → NONE
 */
public enum SubscriptionStatus {

    NONE("구독 안함"),
    BASIC("일반 구독"),
    PREMIUM("프리미엄 구독");

    private final String displayName;

    SubscriptionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean canSubscribeTo(SubscriptionStatus target) {
        return switch (this) {
            case NONE    -> target == BASIC || target == PREMIUM;
            case BASIC   -> target == PREMIUM;
            case PREMIUM -> false;
        };
    }

    public boolean canUnsubscribeTo(SubscriptionStatus target) {
        return switch (this) {
            case PREMIUM -> target == BASIC || target == NONE;
            case BASIC   -> target == NONE;
            case NONE    -> false;
        };
    }
}
