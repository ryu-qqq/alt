package com.ryuqqq.alt.adapter.in.subscription.dto.response;

/**
 * 구독 시도 결과 응답.
 *
 * - attemptId      : 영속화된 시도 ID. 회원만 등록된 케이스(target=NONE)는 null.
 * - status         : COMMITTED / ROLLED_BACK / FAILED. registrationOnly 케이스는 null.
 * - currentStatus  : 처리 후 회원 상태 (NONE / BASIC / PREMIUM)
 * - failureReason  : FAILED / ROLLED_BACK 시 사유 (EXTERNAL_REJECTED / EXTERNAL_TIMEOUT / ...)
 */
public record SubscribeApiResponse(
    Long attemptId,
    String status,
    String currentStatus,
    String failureReason
) {
}
