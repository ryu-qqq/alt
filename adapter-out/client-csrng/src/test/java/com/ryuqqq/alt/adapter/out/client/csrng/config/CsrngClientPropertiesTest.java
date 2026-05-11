package com.ryuqqq.alt.adapter.out.client.csrng.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CsrngClientProperties record 단위 테스트.
 * connectTimeout / readTimeout 의 default 분기를 검증한다.
 */
class CsrngClientPropertiesTest {

    @Test
    @DisplayName("connectTimeout / readTimeout 이 모두 null 이면 2초 default 가 적용된다")
    void shouldApplyDefaultTimeoutsWhenBothNull() {
        // when
        CsrngClientProperties properties = new CsrngClientProperties("https://example.com", null, null);

        // then
        assertThat(properties.baseUrl()).isEqualTo("https://example.com");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("명시적 timeout 은 그대로 보존된다")
    void shouldKeepExplicitTimeouts() {
        // given
        Duration connect = Duration.ofMillis(500);
        Duration read = Duration.ofSeconds(10);

        // when
        CsrngClientProperties properties = new CsrngClientProperties("https://example.com", connect, read);

        // then
        assertThat(properties.connectTimeout()).isEqualTo(connect);
        assertThat(properties.readTimeout()).isEqualTo(read);
    }

    @Test
    @DisplayName("connectTimeout 만 null 이면 그 필드만 default 로 채워진다")
    void shouldFillOnlyConnectTimeoutWhenNull() {
        // when
        CsrngClientProperties properties = new CsrngClientProperties("https://example.com", null, Duration.ofSeconds(5));

        // then
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("readTimeout 만 null 이면 그 필드만 default 로 채워진다")
    void shouldFillOnlyReadTimeoutWhenNull() {
        // when
        CsrngClientProperties properties = new CsrngClientProperties("https://example.com", Duration.ofSeconds(1), null);

        // then
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
    }
}
