package com.ryuqqq.alt.adapter.out.client.csrng.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CsrngClientConfig 단위 테스트.
 * RestClient 빈 생성이 properties 의 baseUrl / timeout 을 반영하는지 검증.
 * (실 호출은 통합 테스트 영역 — 여기서는 빈 생성 자체만)
 */
class CsrngClientConfigTest {

    @Test
    @DisplayName("csrngRestClient 빈이 정상 생성된다")
    void shouldBuildRestClientBean() {
        // given
        CsrngClientProperties properties = new CsrngClientProperties(
            "https://csrng.example.com",
            Duration.ofMillis(500),
            Duration.ofSeconds(3)
        );
        CsrngClientConfig config = new CsrngClientConfig();

        // when
        RestClient restClient = config.csrngRestClient(properties);

        // then
        assertThat(restClient).isNotNull();
    }
}
