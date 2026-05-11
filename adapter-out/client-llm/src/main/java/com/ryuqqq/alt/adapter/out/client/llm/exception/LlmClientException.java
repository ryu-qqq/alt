package com.ryuqqq.alt.adapter.out.client.llm.exception;

/**
 * LLM 호출 실패 예외 베이스.
 * 어댑터 내부 분류용 — 어댑터 outer try/catch 가 LlmSummaryOutcome.Unavailable 로 흡수.
 *
 * httpStatus = -1 은 HTTP 응답이 없는 케이스 (네트워크 오류, 파싱 실패 등).
 */
public abstract class LlmClientException extends RuntimeException {

    private final int httpStatus;

    protected LlmClientException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected LlmClientException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
