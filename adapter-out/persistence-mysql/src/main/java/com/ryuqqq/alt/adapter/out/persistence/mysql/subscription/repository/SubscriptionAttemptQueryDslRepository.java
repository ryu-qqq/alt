package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.QSubscriptionAttemptJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.SubscriptionAttemptJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.condition.SubscriptionAttemptConditions;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionAttemptQueryDslRepository {

    private static final QSubscriptionAttemptJpaEntity ATTEMPT = QSubscriptionAttemptJpaEntity.subscriptionAttemptJpaEntity;

    private final JPAQueryFactory queryFactory;

    public SubscriptionAttemptQueryDslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Optional<SubscriptionAttemptJpaEntity> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(
            queryFactory.selectFrom(ATTEMPT)
                .where(SubscriptionAttemptConditions.idempotencyKeyEq(idempotencyKey))
                .fetchOne()
        );
    }

    /**
     * 회원 이력 — (member_id, requested_at DESC) 인덱스 활용.
     */
    public List<SubscriptionAttemptJpaEntity> findAllByMemberIdOrderByRequestedAtDesc(Long memberId) {
        return queryFactory.selectFrom(ATTEMPT)
            .where(SubscriptionAttemptConditions.memberIdEq(memberId))
            .orderBy(ATTEMPT.requestedAt.desc())
            .fetch();
    }
}
