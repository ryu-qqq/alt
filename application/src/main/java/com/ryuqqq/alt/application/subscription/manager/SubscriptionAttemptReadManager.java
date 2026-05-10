package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class SubscriptionAttemptReadManager {

    private final SubscriptionAttemptQueryPort subscriptionAttemptQueryPort;

    public SubscriptionAttemptReadManager(SubscriptionAttemptQueryPort subscriptionAttemptQueryPort) {
        this.subscriptionAttemptQueryPort = subscriptionAttemptQueryPort;
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionAttempt> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return subscriptionAttemptQueryPort.findByIdempotencyKey(idempotencyKey);
    }

    @Transactional(readOnly = true)
    public SubscriptionHistory findHistoryByMemberId(MemberId memberId) {
        List<SubscriptionAttempt> attempts = subscriptionAttemptQueryPort.findAllByMemberId(memberId);
        return SubscriptionHistory.of(memberId, attempts);
    }
}
