package com.ryuqqq.alt.adapter.out.client.csrng.exception;

/**
 * csrng 호출 실패 예외 베이스. 어댑터 내부 분류용 — application 으로 전파될 때
 * RandomClientException(reason, detail) 으로 번역된다.
 *
 * httpStatus = -1 은 HTTP 응답이 없는 케이스 (네트워크 오류, 파싱 실패 등).
 */
public abstract class CsrngClientException extends RuntimeException {

    private final int httpStatus;

    protected CsrngClientException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected CsrngClientException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
