package com.ryuqqq.alt.application.subscription.dto.response;

/**
 * LLM 요약 호출 결과.
 *
 * - summary            : 정상 응답 본문 (실패 / 호출 불필요 시 null)
 * - unavailableReason  : 실패 사유 (정상 / 호출 불필요 시 null)
 *
 * 정적 팩토리(success / unavailable / empty) 로만 생성하고, 호출자는 isAvailable() 또는 summary() null 체크로 분기한다.
 * 모든 외부 호출 실패 (timeout/rate-limit/circuit-open/api-key 미설정 등) 는 unavailable 로 흡수.
 */
public record LlmSummaryOutcome(
    String summary,
    String unavailableReason
) {

    public static LlmSummaryOutcome success(String summary) {
        return new LlmSummaryOutcome(summary, null);
    }

    public static LlmSummaryOutcome unavailable(String reason) {
        return new LlmSummaryOutcome(null, reason);
    }

    /**
     * LLM 호출이 필요 없는 경우 (예: 이력이 비어있어 요약 대상이 없음).
     * unavailable 과 의미를 구분: 외부 호출 실패가 아니라 호출 자체가 불필요한 상태.
     */
    public static LlmSummaryOutcome empty() {
        return new LlmSummaryOutcome(null, null);
    }

    public boolean isAvailable() {
        return summary != null;
    }
}
