package com.ryuqqq.alt.application.subscription.dto.response;

/**
 * 외부 호출 결과 (정상 응답에 한정).
 * 어댑터(client-csrng)가 csrng 의 random 0/1 같은 raw 응답을 의미 단위로 변환해서 반환한다.
 *
 * - APPROVED: 외부에서 진행 신호 (csrng random=1)
 * - REJECTED: 외부에서 거절 신호 (csrng random=0)
 *
 * 외부 호출 실패(timeout/5xx/CB open 등)는 RandomClientException 으로 throw.
 */
public enum ExternalCallResult {

    APPROVED,
    REJECTED;

    public boolean isApproved() {
        return this == APPROVED;
    }
}
