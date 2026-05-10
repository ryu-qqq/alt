package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

public interface SubscriptionAttemptCommandPort {

    /**
     * SubscriptionAttempt 를 영속화한다. id 가 신규면 INSERT, 아니면 UPDATE.
     *
     * idempotencyKey UNIQUE 제약 위반 시 어댑터는 IdempotencyConflictException 으로 변환해 던진다.
     */
    Long persist(SubscriptionAttempt attempt);
}
