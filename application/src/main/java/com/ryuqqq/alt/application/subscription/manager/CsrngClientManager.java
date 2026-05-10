package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.csrng.CsrngOutcome;
import com.ryuqqq.alt.application.subscription.port.out.CsrngClient;
import org.springframework.stereotype.Component;

/**
 * csrng 호출 래핑. 트랜잭션 없음 (외부 API 호출은 DB 커넥션 점유 회피).
 * Resilience4j 정책은 어댑터(client-csrng) 메서드에 적용된다.
 */
@Component
public class CsrngClientManager {

    private final CsrngClient csrngClient;

    public CsrngClientManager(CsrngClient csrngClient) {
        this.csrngClient = csrngClient;
    }

    public CsrngOutcome fetchRandom() {
        return csrngClient.fetchRandom();
    }
}
