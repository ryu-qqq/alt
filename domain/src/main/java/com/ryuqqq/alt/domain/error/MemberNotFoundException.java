package com.ryuqqq.alt.domain.error;

public class MemberNotFoundException extends SubscriptionException {

    public MemberNotFoundException() {
        super(SubscriptionErrorCode.MEMBER_NOT_FOUND);
    }

    public MemberNotFoundException(String detail) {
        super(SubscriptionErrorCode.MEMBER_NOT_FOUND, detail);
    }
}
