package com.ryuqqq.alt.application.common.factory;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 시간 의존성 단일화. Factory 외에서 직접 주입/사용하지 않는다 (APP-FAC-001).
 * 도메인은 시간을 외부에서 받아야 하므로 Instant.now() 등의 직접 호출이 금지되며,
 * Factory 가 TimeProvider 로부터 받은 시각을 도메인 객체에 전달한다.
 */
public interface TimeProvider {

    Instant now();

    LocalDate today();
}
