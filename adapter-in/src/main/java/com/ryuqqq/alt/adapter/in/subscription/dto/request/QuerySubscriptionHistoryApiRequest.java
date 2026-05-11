package com.ryuqqq.alt.adapter.in.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 구독 이력 조회 요청 API DTO.
 *
 * GET /api/v1/subscriptions/history 의 쿼리 파라미터를 바인딩한다.
 * 휴대폰 번호 형식 검증은 Domain PhoneNumber VO 의 정규식이 담당한다.
 */
public record QuerySubscriptionHistoryApiRequest(

    @NotBlank(message = "휴대폰 번호는 필수입니다")
    String phoneNumber
) {
}
