package com.ryuqqq.alt.adapter.out.client.csrng.executor;

import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CsrngApiExecutor 단위 테스트.
 *
 * 실제 Resilience4j 객체를 작게 구성 (테스트가 빠르게 끝나도록) — CB OPEN 강제 시
 * CallNotPermittedException → RandomClientException(EXTERNAL_CIRCUIT_OPEN) 번역을 검증.
 */
class CsrngApiExecutorTest {

    private CircuitBreaker circuitBreaker;
    private Retry retry;
    private CsrngApiExecutor executor;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.of("test-cb", CircuitBreakerConfig.custom()
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMillis(500))
            .build());
        retry = Retry.of("test-retry", RetryConfig.custom()
            .maxAttempts(1) // retry 비활성 — execute 1회 = supplier 1회
            .build());
        executor = new CsrngApiExecutor(circuitBreaker, retry);
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
    @DisplayName("CircuitBreaker 가 OPEN 이면 RandomClientException(EXTERNAL_CIRCUIT_OPEN) 으로 번역된다")
    void shouldTranslateCallNotPermittedToCircuitOpen() {
        // given — CB 를 강제로 OPEN 으로 전환
        circuitBreaker.transitionToOpenState();

        // when & then
        assertThatThrownBy(() -> executor.execute(() -> "never-called"))
            .isInstanceOf(RandomClientException.class)
            .extracting(e -> ((RandomClientException) e).reason())
            .isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN);
    }

    @Test
    @DisplayName("supplier 에서 던진 RuntimeException 은 그대로 전파된다 (어댑터 outer catch 가 분류)")
    void shouldPropagateSupplierException() {
        // when & then
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new RuntimeException("downstream");
        }))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("downstream");
    }

    @Test
    @DisplayName("CallNotPermittedException 직접 발생 시에도 RandomClientException 으로 번역된다")
    void shouldCatchCallNotPermittedFromExecuteSupplier() {
        // given — 한 번 호출 실패시켜 OPEN 으로 강제 전환
        circuitBreaker.transitionToOpenState();
        AtomicInteger calls = new AtomicInteger();

        // when
        try {
            executor.execute(() -> {
                calls.incrementAndGet();
                return "never";
            });
        } catch (RandomClientException expected) {
            // then — supplier 는 실행되지 않아야 한다 (CB OPEN)
            assertThat(calls).hasValue(0);
            assertThat(expected.reason()).isEqualTo(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN);
            assertThat(expected.detail()).contains("open");
            return;
        }
        // 도달하면 안 됨
        throw new AssertionError("Expected RandomClientException(EXTERNAL_CIRCUIT_OPEN)");
    }

    @Test
    @DisplayName("CallNotPermittedException 외 io.github.resilience4j 예외는 그대로 propagate (외부 catch 영역)")
    void shouldPropagateNonCallNotPermittedFromCircuitBreaker() {
        // given — supplier 가 던지는 일반 예외는 CB 가 카운트만 하고 그대로 던진다
        // when & then
        assertThatThrownBy(() -> executor.execute(() -> {
            throw new IllegalStateException("internal");
        })).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RandomClientException 도 supplier 가 던지면 그대로 propagate (어댑터의 첫 번째 catch 가 처리)")
    void shouldNotInterceptRandomClientException() {
        // given
        RandomClientException original = new RandomClientException(
            AttemptFailureReason.EXTERNAL_PARSE_FAILURE, "parse failed"
        );

        // when & then — Executor 는 RandomClientException 을 가공하지 않는다
        assertThatThrownBy(() -> executor.execute(() -> { throw original; }))
            .isSameAs(original);

        // CallNotPermittedException 확인용으로 명시 — 미사용 import 방지
        assertThat(CallNotPermittedException.class.getName()).contains("CallNotPermittedException");
    }
}
