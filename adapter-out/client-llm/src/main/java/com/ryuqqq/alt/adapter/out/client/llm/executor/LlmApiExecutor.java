package com.ryuqqq.alt.adapter.out.client.llm.executor;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * LLM 외부 호출을 CircuitBreaker(외층) + Retry(내층) 로 보호.
 *
 * csrng Executor 와 다른 점: CallNotPermittedException 을 어댑터에서 직접 잡아
 * LlmSummaryOutcome.Unavailable 로 흡수하므로, 여기서는 별도 변환하지 않고 propagate.
 * (LLM 은 application 레벨 예외 컨트랙트가 없고 graceful degradation 만 있음)
 */
@Component
public class LlmApiExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public LlmApiExecutor(CircuitBreaker llmCircuitBreaker, Retry llmRetry) {
        this.circuitBreaker = llmCircuitBreaker;
        this.retry = llmRetry;
    }

    public <T> T execute(Supplier<T> supplier) {
        Supplier<T> retryDecorated = Retry.decorateSupplier(retry, supplier);
        return circuitBreaker.executeSupplier(retryDecorated);
    }
}
