package com.ryuqqq.alt.adapter.out.client.llm.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmCircuitBreakerConfig 단위 테스트.
 * LLM 전용 CB/Retry 빈 생성과 핵심 파라미터(이름, maxAttempts 2회) 를 검증한다.
 */
class LlmCircuitBreakerConfigTest {

    private final LlmCircuitBreakerConfig config = new LlmCircuitBreakerConfig();

    @Test
    @DisplayName("llmCircuitBreaker 빈이 'llm' 이름으로 생성된다")
    void shouldBuildCircuitBreaker() {
        // when
        CircuitBreaker cb = config.llmCircuitBreaker();

        // then
        assertThat(cb).isNotNull();
        assertThat(cb.getName()).isEqualTo("llm");
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    @DisplayName("llmRetry 빈이 maxAttempts=2 로 생성된다 (LLM 비용 보수적)")
    void shouldBuildRetryWithConservativeAttempts() {
        // when
        Retry retry = config.llmRetry();

        // then
        assertThat(retry).isNotNull();
        assertThat(retry.getName()).isEqualTo("llm");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(2);
    }
}
