package com.ryuqqq.alt.adapter.in.subscription.dto.request;

import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 구독 요청 API DTO.
 *
 * - phoneNumber : 한국 휴대폰 번호 (도메인 PhoneNumber VO 가 정규식 검증)
 * - channelId   : 채널 ID
 * - targetStatus: 변경할 구독 상태 (NONE / BASIC / PREMIUM) — Jackson 이 enum 자동 파싱
 */
public record SubscribeApiRequest(

    @NotBlank(message = "휴대폰 번호는 필수입니다")
    String phoneNumber,

    @NotNull(message = "채널 ID는 필수입니다")
    @Positive(message = "채널 ID는 양수여야 합니다")
    Long channelId,

    @NotNull(message = "변경할 구독 상태는 필수입니다 (NONE / BASIC / PREMIUM)")
    SubscriptionStatus targetStatus
) {
}
