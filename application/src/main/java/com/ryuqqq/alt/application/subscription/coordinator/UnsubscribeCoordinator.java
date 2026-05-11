package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeBundle;
import com.ryuqqq.alt.application.subscription.manager.RandomClientManager;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

/**
 * 해지 사가의 "외부 호출 + 결과 기록" 단계 코디네이터.
 *
 * 번들 내부 메서드(applyApproved / applyRejected / applyFailed / toResult) 만 호출하고,
 * member/attempt 같은 부속 객체에는 직접 손대지 않는다 (LoD 준수).
 *
 * 멱등성 (ADR-0004): SubscribeCoordinator 와 동일 — 충돌 시 IdempotencyConflictException propagate → HTTP 409.
 */
@Component
public class UnsubscribeCoordinator {

    private final RandomClientManager randomClientManager;
    private final SubscriptionPersistenceFacade subscriptionPersistenceFacade;

    public UnsubscribeCoordinator(
        RandomClientManager randomClientManager,
        SubscriptionPersistenceFacade subscriptionPersistenceFacade
    ) {
        this.randomClientManager = randomClientManager;
        this.subscriptionPersistenceFacade = subscriptionPersistenceFacade;
    }

    public UnsubscribeResult coordinate(UnsubscribeBundle bundle) {
        UnsubscribeBundle finalBundle;
        SubscriptionAttempt persisted;

        try {
            ExternalCallResult result = randomClientManager.call();
            if (result.isApproved()) {
                finalBundle = bundle.applyApproved();
                persisted = subscriptionPersistenceFacade.saveWithMemberUpdate(finalBundle.member(), finalBundle.attempt());
            } else {
                finalBundle = bundle.applyRejected();
                persisted = subscriptionPersistenceFacade.saveAttempt(finalBundle.attempt());
            }
        } catch (RandomClientException e) {
            finalBundle = bundle.applyFailed(e.reason(), e.detail());
            persisted = subscriptionPersistenceFacade.saveAttempt(finalBundle.attempt());
        }

        return finalBundle.withPersistedAttempt(persisted).toResult();
    }
}
