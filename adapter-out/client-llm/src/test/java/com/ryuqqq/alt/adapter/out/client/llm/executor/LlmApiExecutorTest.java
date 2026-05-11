package com.ryuqqq.alt.adapter.out.client.llm.executor;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LlmApiExecutor 단위 테스트.
 *
 * csrng 와 차이: CallNotPermittedException 을 잡지 않고 그대로 propagate.
 * (어댑터 outer catch 가 unavailable 로 흡수하기 때문)
 */
class LlmApiExecutorTest {

    private CircuitBreaker cb;
    private Retry retry;
    private LlmApiExecutor executor;

    @BeforeEach
    void setUp() {
        cb = CircuitBreaker.of("test-llm-cb", CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMillis(500))
            .build());
        retry = Retry.of("test-llm-retry", RetryConfig.custom()
            .maxAttempts(1)
            .build());
        executor = new LlmApiExecutor(cb, retry);
    }

    @Test
    @DisplayName("정상 supplier 는 그대로 결과를 반환한다")
    void shouldReturnSupplierResult() {
        // when
        String result = executor.execute(() -> "ok");

        // then
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("CB OPEN 이면 CallNotPermittedException 을 변환 없이 그대로 propagate 한다")
    void shouldPropagateCallNotPermitted() {
        // given
        cb.transitionToOpenState();

        // when & then
        assertThatThrownBy(() -> executor.execute(() -> "never"))
            .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("supplier 가 던지는 예외는 그대로 propagate 된다")
    void shouldPropagateSupplierException() {
        // when & then
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new RuntimeException("downstream");
        }))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("downstream");
    }
}
