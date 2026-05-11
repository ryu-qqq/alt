package com.ryuqqq.alt.adapter.out.client.llm.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Llm 어댑터 내부 분류 예외들의 메타데이터 검증.
 * httpStatus / message / cause 가 어댑터의 로깅·번역 분기에서 사용되므로 정확성을 보장한다.
 */
class LlmExceptionTest {

    @Test
    @DisplayName("LlmServerException 은 5xx status 와 prefix 메시지를 보존한다")
    void llmServerException() {
        // when
        LlmServerException e = new LlmServerException(503, "service unavailable");

        // then
        assertThat(e.httpStatus()).isEqualTo(503);
        assertThat(e.getMessage()).contains("llm server error").contains("service unavailable");
    }

    @Test
    @DisplayName("LlmBadRequestException 은 4xx status 와 prefix 메시지를 보존한다")
    void llmBadRequestException() {
        // when
        LlmBadRequestException e = new LlmBadRequestException(400, "bad params");

        // then
        assertThat(e.httpStatus()).isEqualTo(400);
        assertThat(e.getMessage()).contains("llm bad request").contains("bad params");
    }

    @Test
    @DisplayName("LlmRateLimitException 은 429 status 와 prefix 메시지를 보존한다")
    void llmRateLimitException() {
        // when
        LlmRateLimitException e = new LlmRateLimitException("too many requests");

        // then
        assertThat(e.httpStatus()).isEqualTo(429);
        assertThat(e.getMessage()).contains("llm rate limit").contains("too many requests");
    }

    @Test
    @DisplayName("LlmNetworkException 은 httpStatus=-1 과 cause 를 보존한다")
    void llmNetworkException() {
        // given
        Throwable cause = new RuntimeException("socket timeout");

        // when
        LlmNetworkException e = new LlmNetworkException("read timed out", cause);

        // then
        assertThat(e.httpStatus()).isEqualTo(-1);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).contains("llm network error").contains("read timed out");
    }

    @Test
    @DisplayName("LlmParseException 은 httpStatus=-1 + parse 컨텍스트를 보존한다")
    void llmParseException() {
        // when
        LlmParseException e = new LlmParseException("unexpected status");

        // then
        assertThat(e.httpStatus()).isEqualTo(-1);
        assertThat(e.getMessage()).contains("llm parse error").contains("unexpected status");
    }
}
