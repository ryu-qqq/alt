package com.ryuqqq.alt.adapter.out.client.csrng.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * csrng-client.yml 의 csrng-client.* 설정 바인딩.
 */
@ConfigurationProperties(prefix = "csrng-client")
public record CsrngClientProperties(
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout
) {

    public CsrngClientProperties {
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(2);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(2);
    }
}
