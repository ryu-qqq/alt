package com.ryuqqq.alt.adapter.out.client.csrng.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Csrng 어댑터 내부 분류 예외들의 메타데이터 검증.
 * httpStatus / message 가 어댑터의 로깅·번역 분기에서 사용되므로 정확성을 보장한다.
 */
class CsrngExceptionTest {

    @Test
    @DisplayName("CsrngServerException 은 5xx status 와 prefix 가 포함된 메시지를 보존한다")
    void csrngServerException() {
        // when
        CsrngServerException e = new CsrngServerException(503, "service unavailable");

        // then
        assertThat(e.httpStatus()).isEqualTo(503);
        assertThat(e.getMessage()).contains("csrng server error").contains("service unavailable");
    }

    @Test
    @DisplayName("CsrngBadRequestException 은 4xx status 와 prefix 가 포함된 메시지를 보존한다")
    void csrngBadRequestException() {
        // when
        CsrngBadRequestException e = new CsrngBadRequestException(400, "bad params");

        // then
        assertThat(e.httpStatus()).isEqualTo(400);
        assertThat(e.getMessage()).contains("csrng bad request").contains("bad params");
    }

    @Test
    @DisplayName("CsrngNetworkException 은 httpStatus=-1 과 cause 를 보존한다")
    void csrngNetworkException() {
        // given
        Throwable cause = new RuntimeException("socket timeout");

        // when
        CsrngNetworkException e = new CsrngNetworkException("read timed out", cause);

        // then
        assertThat(e.httpStatus()).isEqualTo(-1);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).contains("csrng network error").contains("read timed out");
    }

    @Test
    @DisplayName("CsrngParseException 은 httpStatus=-1 + parse 컨텍스트를 보존한다")
    void csrngParseException() {
        // when
        CsrngParseException e = new CsrngParseException("unexpected random=99");

        // then
        assertThat(e.httpStatus()).isEqualTo(-1);
        assertThat(e.getMessage()).contains("csrng parse error").contains("unexpected random=99");
    }
}
