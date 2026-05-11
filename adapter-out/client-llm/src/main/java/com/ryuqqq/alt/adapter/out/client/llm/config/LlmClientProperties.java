package com.ryuqqq.alt.adapter.out.client.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * llm-client.yml 의 llm-client.* 설정 바인딩.
 * api-key 는 환경변수 OPENAI_API_KEY 로 주입 (yml 내 ${OPENAI_API_KEY:}).
 */
@ConfigurationProperties(prefix = "llm-client")
public record LlmClientProperties(
    String baseUrl,
    String apiKey,
    String model,
    Integer maxTokens,
    Double temperature,
    Duration connectTimeout,
    Duration readTimeout
) {

    public LlmClientProperties {
        if (model == null || model.isBlank()) model = "gpt-4o-mini";
        if (maxTokens == null) maxTokens = 500;
        if (temperature == null) temperature = 0.3;
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(15);
    }
}
