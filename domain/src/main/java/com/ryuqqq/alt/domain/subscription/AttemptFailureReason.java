package com.ryuqqq.alt.domain.subscription;

/**
 * 시도 종결 사유. 도메인은 외부 implementation(csrng 등)을 모르므로 generic 명명.
 *
 * - EXTERNAL_REJECTED       : ROLLED_BACK 사유 (외부가 명시적 거절)
 * - EXTERNAL_TIMEOUT        : FAILED — 외부 호출 타임아웃
 * - EXTERNAL_SERVER_ERROR   : FAILED — 외부 5xx 응답
 * - EXTERNAL_CLIENT_ERROR   : FAILED — 외부 4xx 응답
 * - EXTERNAL_CIRCUIT_OPEN   : FAILED — CircuitBreaker OPEN
 * - EXTERNAL_PARSE_FAILURE  : FAILED — 외부 응답 파싱 실패
 * - EXTERNAL_UNKNOWN        : FAILED — 분류되지 않은 실패
 */
public enum AttemptFailureReason {

    EXTERNAL_REJECTED("외부 응답으로 인한 의도된 롤백"),
    EXTERNAL_TIMEOUT("외부 호출 타임아웃"),
    EXTERNAL_SERVER_ERROR("외부 5xx 응답"),
    EXTERNAL_CLIENT_ERROR("외부 4xx 응답"),
    EXTERNAL_CIRCUIT_OPEN("CircuitBreaker OPEN"),
    EXTERNAL_PARSE_FAILURE("외부 응답 파싱 실패"),
    EXTERNAL_UNKNOWN("분류되지 않은 실패");

    private final String displayName;

    AttemptFailureReason(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
