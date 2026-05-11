package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.SubscriptionAttemptJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.SubscriptionAttemptEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.SubscriptionAttemptJpaRepository;
import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptCommandPort;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionAttemptCommandAdapter implements SubscriptionAttemptCommandPort {

    private final SubscriptionAttemptJpaRepository subscriptionAttemptJpaRepository;
    private final SubscriptionAttemptEntityMapper subscriptionAttemptEntityMapper;

    public SubscriptionAttemptCommandAdapter(
        SubscriptionAttemptJpaRepository subscriptionAttemptJpaRepository,
        SubscriptionAttemptEntityMapper subscriptionAttemptEntityMapper
    ) {
        this.subscriptionAttemptJpaRepository = subscriptionAttemptJpaRepository;
        this.subscriptionAttemptEntityMapper = subscriptionAttemptEntityMapper;
    }

    @Override
    public Long persist(SubscriptionAttempt attempt) {
        SubscriptionAttemptJpaEntity entity = subscriptionAttemptEntityMapper.toEntity(attempt);
        try {
            return subscriptionAttemptJpaRepository.save(entity).getId();
        } catch (DataIntegrityViolationException e) {
            // idempotency_key UNIQUE 충돌을 도메인 예외로 변환 (ADR-0004)
            if (attempt.idempotencyKey() != null) {
                throw new IdempotencyConflictException(attempt.idempotencyKey());
            }
            throw e;
        }
    }
}
