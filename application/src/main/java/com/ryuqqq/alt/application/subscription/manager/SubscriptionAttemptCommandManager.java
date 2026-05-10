package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptCommandPort;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionAttemptCommandManager {

    private final SubscriptionAttemptCommandPort subscriptionAttemptCommandPort;

    public SubscriptionAttemptCommandManager(SubscriptionAttemptCommandPort subscriptionAttemptCommandPort) {
        this.subscriptionAttemptCommandPort = subscriptionAttemptCommandPort;
    }

    @Transactional
    public Long persist(SubscriptionAttempt attempt) {
        return subscriptionAttemptCommandPort.persist(attempt);
    }
}
