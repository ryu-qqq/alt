package com.ryuqqq.alt.adapter.out.client.csrng.exception;

/**
 * csrng 응답 파싱 실패 (빈 배열, JSON 깨짐, 예상 외 값 등). Retry / CB ignore.
 */
public class CsrngParseException extends CsrngClientException {

    public CsrngParseException(String message) {
        super("csrng parse error: " + message, -1);
    }
}
