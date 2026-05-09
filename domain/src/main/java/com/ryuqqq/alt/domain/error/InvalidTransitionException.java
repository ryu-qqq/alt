package com.ryuqqq.alt.domain.error;

public class InvalidTransitionException extends DomainException {

    public InvalidTransitionException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
