package com.ryuqqq.alt.domain.member;

/**
 * 회원이 가질 수 있는 구독 상태와 전이 규칙.
 * 전이 규칙은 enum 자체에 캡슐화하여 도메인 어디서든 동일 규칙으로 검증되도록 한다.
 */
public enum SubscriptionStatus {

    NONE,
    BASIC,
    PREMIUM;

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
