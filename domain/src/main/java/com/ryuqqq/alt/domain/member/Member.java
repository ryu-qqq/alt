package com.ryuqqq.alt.domain.member;

import com.ryuqqq.alt.domain.error.InvalidTransitionException;

public final class Member {

    private final MemberId id;
    private final PhoneNumber phoneNumber;
    private SubscriptionStatus status;

    private Member(MemberId id, PhoneNumber phoneNumber, SubscriptionStatus status) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.status = status;
    }

    public static Member newMember(MemberId id, PhoneNumber phoneNumber, SubscriptionStatus initial) {
        return new Member(id, phoneNumber, initial);
    }

    public static Member rehydrate(MemberId id, PhoneNumber phoneNumber, SubscriptionStatus status) {
        return new Member(id, phoneNumber, status);
    }

    public void applySubscribe(SubscriptionStatus target) {
        if (!status.canSubscribeTo(target)) {
            throw new InvalidTransitionException(
                "subscribe transition forbidden: " + status + " -> " + target);
        }
        this.status = target;
    }

    public void applyUnsubscribe(SubscriptionStatus target) {
        if (!status.canUnsubscribeTo(target)) {
            throw new InvalidTransitionException(
                "unsubscribe transition forbidden: " + status + " -> " + target);
        }
        this.status = target;
    }

    public MemberId id() {
        return id;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public SubscriptionStatus status() {
        return status;
    }
}
