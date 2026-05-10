package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

import java.util.List;
import java.util.Optional;

public interface SubscriptionAttemptQueryPort {

    /**
     * 멱등성 키로 기존 시도를 조회. 동일 키 재요청을 식별하는 데 사용 (ADR-0004).
     */
    Optional<SubscriptionAttempt> findByIdempotencyKey(String idempotencyKey);

    /**
     * 회원의 모든 시도 이력 (모든 상태 포함). 정렬은 어댑터 책임.
     */
    List<SubscriptionAttempt> findAllByMemberId(MemberId memberId);
}
