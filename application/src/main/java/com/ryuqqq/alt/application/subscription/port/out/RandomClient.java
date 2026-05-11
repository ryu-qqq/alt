package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;

/**
 * 외부 random 신호 Port. 어댑터가 csrng 등의 raw 응답(0/1) 을 ExternalCallResult 로 변환해 반환한다.
 * 어떤 종류의 호출 실패(timeout/5xx/CB open/파싱 실패 등) 도 RandomClientException 으로 throw.
 *
 * Resilience4j 정책은 어댑터(client-csrng) 메서드에 적용된다.
 */
public interface RandomClient {

    ExternalCallResult call();
}
