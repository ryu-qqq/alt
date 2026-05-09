package com.ryuqqq.alt.domain.error;

public class InvalidPhoneNumberException extends SubscriptionException {

    public InvalidPhoneNumberException(String detail) {
        super(SubscriptionErrorCode.INVALID_PHONE_NUMBER, detail);
    }
}
