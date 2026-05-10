package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.application.subscription.dto.csrng.CsrngOutcome;

/**
 * csrng 외부 API Client Port.
 * 어댑터 구현은 RestClient + Resilience4j (CircuitBreaker / Retry / TimeLimiter) 를 적용한다.
 * 어떤 실패 케이스든 예외를 throw 하지 않고 CsrngOutcome.Unavailable 로 변환해 반환한다.
 */
public interface CsrngClient {

    CsrngOutcome fetchRandom();
}
