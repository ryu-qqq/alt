package com.ryuqqq.alt.domain.error;

/**
 * 모든 도메인 예외의 베이스. ErrorCode를 보유하여 API 레이어가 일관되게 매핑하도록 한다 (DOM-EXC-001).
 * Checked Exception은 사용하지 않는다.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    protected DomainException(ErrorCode errorCode, String detail) {
        super(errorCode.message() + " : " + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
