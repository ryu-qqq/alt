package com.ryuqqq.alt.domain.subscription;

public enum AttemptFailureReason {

    CSRNG_REJECTED,    // random=0 응답으로 의도된 롤백
    CSRNG_UNAVAILABLE  // 외부 API 장애 (timeout / 5xx / circuit open 등)
}
