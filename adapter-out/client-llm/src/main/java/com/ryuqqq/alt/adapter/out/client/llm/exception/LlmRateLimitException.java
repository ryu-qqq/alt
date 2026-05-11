package com.ryuqqq.alt.adapter.out.client.llm.exception;

/**
 * LLM 429 응답. Retry 대상이지만 (보수적으로) 재시도 횟수 제한 + 지수 백오프 적용.
 * Retry-After 헤더 미반영 (단순화) — 빅테크는 헤더 기반 동적 wait 가능하나 과제 범위 초과.
 */
public class LlmRateLimitException extends LlmClientException {

    public LlmRateLimitException(String message) {
        super("llm rate limit: " + message, 429);
    }
}
