package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.SubscribeBundle;
import com.ryuqqq.alt.application.subscription.manager.RandomClientManager;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

/**
 * 구독 사가의 "외부 호출 + 결과 기록" 단계 코디네이터.
 *
 * 번들 내부 메서드(applyApproved / applyRejected / applyFailed / toResult) 만 호출하고,
 * member/attempt 같은 부속 객체에는 직접 손대지 않는다 (LoD 준수).
 *
 * 멱등성 (ADR-0004):
 * - 동일 idempotencyKey 로 재호출 시 어댑터가 IdempotencyConflictException 을 던지고
 *   본 코디네이터는 잡지 않고 그대로 propagate → ErrorMapper 가 HTTP 409 로 변환.
 * - 클라이언트는 매 요청마다 새 UUID 를 사용해야 한다 (표준 Idempotency-Key 컨트랙트).
 */
@Component
public class SubscribeCoordinator {

    private final RandomClientManager randomClientManager;
    private final SubscriptionPersistenceFacade subscriptionPersistenceFacade;

    public SubscribeCoordinator(
        RandomClientManager randomClientManager,
        SubscriptionPersistenceFacade subscriptionPersistenceFacade
    ) {
        this.randomClientManager = randomClientManager;
        this.subscriptionPersistenceFacade = subscriptionPersistenceFacade;
    }

    public SubscribeResult coordinate(SubscribeBundle bundle) {
        SubscribeBundle finalBundle;
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
