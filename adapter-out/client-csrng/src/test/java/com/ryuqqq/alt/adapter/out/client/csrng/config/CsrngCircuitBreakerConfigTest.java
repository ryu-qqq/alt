package com.ryuqqq.alt.adapter.out.client.csrng.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CsrngCircuitBreakerConfig 단위 테스트.
 * Resilience4j CircuitBreaker / Retry 빈 생성을 검증한다.
 */
class CsrngCircuitBreakerConfigTest {

    private final CsrngCircuitBreakerConfig config = new CsrngCircuitBreakerConfig();

    @Test
    @DisplayName("csrngCircuitBreaker 빈이 정상 생성되고 이름은 'csrng'")
    void shouldBuildCircuitBreakerBean() {
        // when
        CircuitBreaker circuitBreaker = config.csrngCircuitBreaker();

        // then
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getName()).isEqualTo("csrng");
        // 실패율 threshold = 50%
        assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    @DisplayName("csrngRetry 빈이 정상 생성되고 maxAttempts=3")
    void shouldBuildRetryBean() {
        // when
        Retry retry = config.csrngRetry();

        // then
        assertThat(retry).isNotNull();
        assertThat(retry.getName()).isEqualTo("csrng");
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }
}
