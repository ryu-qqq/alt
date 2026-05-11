package com.ryuqqq.alt.application.common.factory;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 시스템 시계 기반 TimeProvider 구현체.
 *
 * 운영 환경에서 사용하며, 테스트에서는 고정 시각을 반환하는 Mock/Stub 으로 대체한다.
 * bootstrap 모듈의 @SpringBootApplication scanBasePackages 가 application 패키지를 포함하므로
 * 별도 @Bean 등록 없이 자동 발견된다.
 */
@Component
public class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public LocalDate today() {
        return LocalDate.now();
    }
}
