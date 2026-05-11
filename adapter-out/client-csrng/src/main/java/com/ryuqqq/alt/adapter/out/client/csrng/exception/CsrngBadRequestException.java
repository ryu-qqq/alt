package com.ryuqqq.alt.adapter.out.client.csrng.exception;

/**
 * csrng 4xx 응답. Resilience4j Retry / CircuitBreaker 둘 다 ignore (재시도 무의미).
 */
public class CsrngBadRequestException extends CsrngClientException {

    public CsrngBadRequestException(int httpStatus, String message) {
        super("csrng bad request: " + message, httpStatus);
    }
}
