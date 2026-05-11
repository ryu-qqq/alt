package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.SubscriptionAttemptEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.SubscriptionAttemptQueryDslRepository;
import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SubscriptionAttemptQueryAdapter implements SubscriptionAttemptQueryPort {

    private final SubscriptionAttemptQueryDslRepository subscriptionAttemptQueryDslRepository;
    private final SubscriptionAttemptEntityMapper subscriptionAttemptEntityMapper;

    public SubscriptionAttemptQueryAdapter(
        SubscriptionAttemptQueryDslRepository subscriptionAttemptQueryDslRepository,
        SubscriptionAttemptEntityMapper subscriptionAttemptEntityMapper
    ) {
        this.subscriptionAttemptQueryDslRepository = subscriptionAttemptQueryDslRepository;
        this.subscriptionAttemptEntityMapper = subscriptionAttemptEntityMapper;
    }

    @Override
    public Optional<SubscriptionAttempt> findByIdempotencyKey(String idempotencyKey) {
        return subscriptionAttemptQueryDslRepository.findByIdempotencyKey(idempotencyKey)
            .map(subscriptionAttemptEntityMapper::toDomain);
    }

    @Override
    public List<SubscriptionAttempt> findAllByMemberId(MemberId memberId) {
        return subscriptionAttemptQueryDslRepository.findAllByMemberIdOrderByRequestedAtDesc(memberId.value())
            .stream()
            .map(subscriptionAttemptEntityMapper::toDomain)
            .toList();
    }
}
