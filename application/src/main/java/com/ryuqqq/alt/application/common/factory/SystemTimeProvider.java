package com.ryuqqq.alt.application.common.factory;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 시스템 시계 기반 기본 구현. 테스트에서는 FixedTimeProvider 등으로 교체 가능.
 */
@Component
public class SystemTimeProvider implements TimeProvider {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(KST);
    }
}
