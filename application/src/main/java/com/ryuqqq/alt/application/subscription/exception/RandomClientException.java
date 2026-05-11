package com.ryuqqq.alt.application.subscription.exception;

import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;

/**
 * 외부 random 신호 호출 실패를 나타내는 예외. 어댑터가 분류해서 throw 한다.
 *
 * reason  : 도메인 enum 으로 분류된 실패 유형 (TIMEOUT/SERVER_ERROR/CIRCUIT_OPEN 등)
 * detail  : 운영 디버깅용 메시지 (HTTP status, raw payload 등 어댑터 컨텍스트)
 *
 * Application 레이어가 이 예외를 잡아 SubscriptionAttempt(FAILED, reason, detail) 로 박제한다.
 */
public class RandomClientException extends RuntimeException {

    private final AttemptFailureReason reason;
    private final String detail;

    public RandomClientException(AttemptFailureReason reason, String detail) {
        super(reason.name() + ": " + detail);
        this.reason = reason;
        this.detail = detail;
    }

    public AttemptFailureReason reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }
}
