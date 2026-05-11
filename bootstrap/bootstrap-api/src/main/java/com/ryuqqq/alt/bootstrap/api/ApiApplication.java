package com.ryuqqq.alt.bootstrap.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Alt API 서버 진입점 (구독 / 해지 / 이력 조회).
 *
 * <p>{@code scanBasePackages} 를 명시해 application / adapter-in / adapter-out 모듈의 빈을 모두 발견한다.
 * JPA 리포지토리/엔티티 스캔은 어댑터 모듈의 {@code PersistenceMysqlConfig} 가 책임진다.
 *
 * @author ryu-qqq
 */
@SpringBootApplication(scanBasePackages = "com.ryuqqq.alt")
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
