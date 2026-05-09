package com.ryuqqq.alt.domain.error;

/**
 * 구독 도메인(BC)의 예외 베이스. 단일 BC 프로젝트라 BC별 베이스가 1개다.
 * 모든 구체 예외는 이 클래스를 상속한다 (DOM-EXC-001).
 */
public abstract class SubscriptionException extends DomainException {

    protected SubscriptionException(ErrorCode errorCode) {
        super(errorCode);
    }

    protected SubscriptionException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
