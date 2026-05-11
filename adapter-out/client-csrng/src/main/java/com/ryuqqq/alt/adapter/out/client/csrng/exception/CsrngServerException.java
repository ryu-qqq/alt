package com.ryuqqq.alt.adapter.out.client.csrng.exception;

/**
 * csrng 5xx 응답. Resilience4j Retry 대상.
 */
public class CsrngServerException extends CsrngClientException {

    public CsrngServerException(int httpStatus, String message) {
        super("csrng server error: " + message, httpStatus);
    }
}
