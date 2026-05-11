package com.ryuqqq.alt.adapter.out.client.llm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * LLM API 호출 전용 RestClient.
 *
 * - baseUrl / timeout 외부화
 * - Authorization Bearer 헤더를 RestClient builder 에 default 로 박아 호출처에서 매번 설정 안 함
 * - Content-Type: application/json default
 */
@Configuration
@EnableConfigurationProperties(LlmClientProperties.class)
public class LlmClientConfig {

    @Bean
    public RestClient llmRestClient(LlmClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        factory.setReadTimeout((int) properties.readTimeout().toMillis());

        RestClient.Builder builder = RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }

        return builder.build();
    }
}
