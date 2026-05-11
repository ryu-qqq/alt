package com.ryuqqq.alt.adapter.in;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * adapter-in 모듈 @WebMvcTest 슬라이스 테스트가 필요한 @SpringBootConfiguration.
 * 실제 부트스트랩 (bootstrap 모듈) 은 adapter-in 의존성에 들어있지 않으므로
 * 테스트 전용으로 컨텍스트 root 를 제공한다.
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
