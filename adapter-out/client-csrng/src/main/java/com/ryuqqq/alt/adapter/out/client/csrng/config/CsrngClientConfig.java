package com.ryuqqq.alt.adapter.out.client.csrng.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * csrng API 호출 전용 RestClient 빈 + Properties 활성화.
 * baseUrl / timeout 은 csrng-client.yml 에 외부화.
 */
@Configuration
@EnableConfigurationProperties(CsrngClientProperties.class)
public class CsrngClientConfig {

    @Bean
    public RestClient csrngRestClient(CsrngClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .build();
    }
}
