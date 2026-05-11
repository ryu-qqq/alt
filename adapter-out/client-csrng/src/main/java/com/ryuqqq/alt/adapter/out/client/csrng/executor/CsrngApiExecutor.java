package com.ryuqqq.alt.adapter.out.client.csrng.executor;

import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * csrng 외부 호출을 CircuitBreaker(외층) + Retry(내층) 로 보호하는 실행기.
 *
 * 데코레이터 순서가 중요:
 *   - Retry 가 내층 → 한 호출에서 발생한 N 번의 시도를 합쳐서 CB 에 1건 으로 카운트
 *   - CB 가 외층 → "논리적 호출 단위" 로 실패율 측정 (Retry 외층이면 N 배 빨리 OPEN)
 *
 * CB OPEN 시 발생하는 CallNotPermittedException 는 RandomClientException(EXTERNAL_CIRCUIT_OPEN)
 * 으로 즉시 번역 — application 레이어가 인식하는 단일 예외 컨트랙트 유지.
 */
@Component
public class CsrngApiExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CsrngApiExecutor(CircuitBreaker csrngCircuitBreaker, Retry csrngRetry) {
        this.circuitBreaker = csrngCircuitBreaker;
        this.retry = csrngRetry;
    }

    public <T> T execute(Supplier<T> supplier) {
        Supplier<T> retryDecorated = Retry.decorateSupplier(retry, supplier);
        try {
            return circuitBreaker.executeSupplier(retryDecorated);
        } catch (CallNotPermittedException e) {
            throw new RandomClientException(
                AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN,
                "csrng circuit breaker is open"
            );
        }
    }
}
