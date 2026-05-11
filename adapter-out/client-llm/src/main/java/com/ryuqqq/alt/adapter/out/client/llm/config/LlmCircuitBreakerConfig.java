package com.ryuqqq.alt.adapter.out.client.llm.config;

import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmBadRequestException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmNetworkException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmParseException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmRateLimitException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmServerException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 외부 API 회복탄력성 설정 — Java Bean 기반 (csrng 와 동일 패턴).
 *
 * csrng 대비 차이:
 *   - Retry maxAttempts 보수적 (2회) — LLM 호출 비용 큼
 *   - 429 RateLimit 도 retry 대상
 *   - 4xx (BadRequest) / 파싱 실패는 ignore
 *   - slowCallDurationThreshold 더 큼 (LLM 응답 생성 시간 ↑)
 */
@Configuration
public class LlmCircuitBreakerConfig {

    @Bean
    public CircuitBreaker llmCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(3)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(
                LlmServerException.class,
                LlmNetworkException.class,
                LlmRateLimitException.class
            )
            .ignoreExceptions(
                LlmBadRequestException.class,
                LlmParseException.class
            )
            .build();

        return CircuitBreaker.of("llm", config);
    }

    @Bean
    public Retry llmRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(2)
            .intervalFunction(attempt -> 500L * (long) Math.pow(2, attempt - 1))
            .retryExceptions(
                LlmServerException.class,
                LlmNetworkException.class,
                LlmRateLimitException.class
            )
            .ignoreExceptions(
                LlmBadRequestException.class,
                LlmParseException.class
            )
            .build();

        return Retry.of("llm", config);
    }
}
