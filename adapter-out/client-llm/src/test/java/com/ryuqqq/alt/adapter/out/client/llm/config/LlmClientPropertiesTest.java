package com.ryuqqq.alt.adapter.out.client.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmClientProperties record 단위 테스트.
 * model / maxTokens / temperature / timeouts default 분기를 검증.
 */
class LlmClientPropertiesTest {

    @Test
    @DisplayName("모든 옵셔널 필드가 null 이면 default 값으로 채워진다")
    void shouldApplyAllDefaultsWhenAllNull() {
        // when
        LlmClientProperties properties = new LlmClientProperties(
            "https://api.openai.com",
            "sk-key",
            null, null, null, null, null
        );

        // then
        assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.apiKey()).isEqualTo("sk-key");
        assertThat(properties.model()).isEqualTo("gpt-4o-mini");
        assertThat(properties.maxTokens()).isEqualTo(500);
        assertThat(properties.temperature()).isEqualTo(0.3);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("model 이 blank 면 default 로 채워진다")
    void shouldFallBackToDefaultModelWhenBlank() {
        // when
        LlmClientProperties properties = new LlmClientProperties(
            "https://api.openai.com", "k", "   ", null, null, null, null
        );

        // then
        assertThat(properties.model()).isEqualTo("gpt-4o-mini");
    }

    @Test
    @DisplayName("명시적 값이 주어지면 그 값이 보존된다")
    void shouldKeepExplicitValues() {
        // when
        LlmClientProperties properties = new LlmClientProperties(
            "https://example.com",
            "k",
            "gpt-4",
            1000,
            0.7,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30)
        );

        // then
        assertThat(properties.model()).isEqualTo("gpt-4");
        assertThat(properties.maxTokens()).isEqualTo(1000);
        assertThat(properties.temperature()).isEqualTo(0.7);
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
