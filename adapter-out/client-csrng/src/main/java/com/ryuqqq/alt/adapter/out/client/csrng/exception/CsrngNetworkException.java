package com.ryuqqq.alt.adapter.out.client.csrng.exception;

/**
 * csrng 호출 시 네트워크 오류 (timeout / connection refused / TLS 등). Retry 대상.
 */
public class CsrngNetworkException extends CsrngClientException {

    public CsrngNetworkException(String message, Throwable cause) {
        super("csrng network error: " + message, -1, cause);
    }
}
