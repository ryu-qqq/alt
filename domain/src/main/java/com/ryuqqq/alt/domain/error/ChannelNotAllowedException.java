package com.ryuqqq.alt.domain.error;

public class ChannelNotAllowedException extends DomainException {

    public ChannelNotAllowedException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
