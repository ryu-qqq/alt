package com.ryuqqq.alt.adapter.out.client.csrng.config;

import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngBadRequestException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngNetworkException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngParseException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngServerException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * csrng 외부 API 회복탄력성 설정 — Java Bean 기반.
 *
 * - CircuitBreaker : 50% 실패율 / 슬라이딩 윈도우 20 / OPEN 60s. 4xx/파싱 실패는 ignore.
 * - Retry          : 3회, 100ms × 2^(n-1) exponential backoff. 재시도 가치 있는 예외만.
 */
@Configuration
public class CsrngCircuitBreakerConfig {

    @Bean
    public CircuitBreaker csrngCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(20)
            .minimumNumberOfCalls(10)
            .permittedNumberOfCallsInHalfOpenState(5)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(CsrngServerException.class, CsrngNetworkException.class)
            .ignoreExceptions(CsrngBadRequestException.class, CsrngParseException.class)
            .build();

        return CircuitBreaker.of("csrng", config);
    }

    @Bean
    public Retry csrngRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(attempt -> 100L * (long) Math.pow(2, attempt - 1))
            .retryExceptions(CsrngServerException.class, CsrngNetworkException.class)
            .ignoreExceptions(CsrngBadRequestException.class, CsrngParseException.class)
            .build();

        return Retry.of("csrng", config);
    }
}
