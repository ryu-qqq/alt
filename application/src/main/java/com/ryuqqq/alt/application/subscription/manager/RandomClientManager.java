package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.port.out.RandomClient;
import org.springframework.stereotype.Component;

/**
 * 외부 random 신호 호출 래퍼. 트랜잭션 없음 (외부 API 호출은 DB 커넥션 점유 회피).
 * RandomClient 의 예외(RandomClientException) 는 그대로 propagate.
 *
 * 향후 메트릭/로깅/비즈니스 fallback 추가 자리.
 */
@Component
public class RandomClientManager {

    private final RandomClient randomClient;

    public RandomClientManager(RandomClient randomClient) {
        this.randomClient = randomClient;
    }

    public ExternalCallResult call() {
        return randomClient.call();
    }
}
