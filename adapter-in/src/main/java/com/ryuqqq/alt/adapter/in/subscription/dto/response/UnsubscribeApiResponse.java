package com.ryuqqq.alt.adapter.in.subscription.dto.response;

/**
 * 구독 해지 시도 결과 응답.
 *
 * - attemptId      : 영속화된 시도 ID
 * - status         : COMMITTED / ROLLED_BACK / FAILED
 * - currentStatus  : 처리 후 회원 상태
 * - failureReason  : FAILED / ROLLED_BACK 시 사유
 */
public record UnsubscribeApiResponse(
    Long attemptId,
    String status,
    String currentStatus,
    String failureReason
) {
}
