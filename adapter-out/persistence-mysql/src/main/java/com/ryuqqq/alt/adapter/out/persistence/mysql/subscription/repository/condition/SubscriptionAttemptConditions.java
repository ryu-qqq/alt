package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.condition;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.QSubscriptionAttemptJpaEntity;

public final class SubscriptionAttemptConditions {

    private static final QSubscriptionAttemptJpaEntity ATTEMPT = QSubscriptionAttemptJpaEntity.subscriptionAttemptJpaEntity;

    private SubscriptionAttemptConditions() {
    }

    public static BooleanExpression idempotencyKeyEq(String idempotencyKey) {
        return idempotencyKey != null ? ATTEMPT.idempotencyKey.eq(idempotencyKey) : null;
    }

    public static BooleanExpression memberIdEq(Long memberId) {
        return memberId != null ? ATTEMPT.memberId.eq(memberId) : null;
    }
}
