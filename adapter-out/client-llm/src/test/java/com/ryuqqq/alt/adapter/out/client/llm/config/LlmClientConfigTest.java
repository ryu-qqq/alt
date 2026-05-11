package com.ryuqqq.alt.adapter.out.client.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmClientConfig 단위 테스트.
 * baseUrl / timeout / api-key 분기 (있/없) 모두 빈 생성 자체는 성공한다.
 */
class LlmClientConfigTest {

    private final LlmClientConfig config = new LlmClientConfig();

    @Test
    @DisplayName("api-key 가 있으면 Authorization 헤더가 포함된 RestClient 가 생성된다")
    void shouldBuildClientWithAuthorizationHeader() {
        // given
        LlmClientProperties properties = new LlmClientProperties(
            "https://api.openai.com",
            "sk-xxx",
            "gpt-4o-mini",
            500, 0.3,
            Duration.ofSeconds(3), Duration.ofSeconds(15)
        );

        // when
        RestClient client = config.llmRestClient(properties);

        // then — 빌드 자체가 성공하면 OK (실제 헤더 행동은 통합 테스트 영역)
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("api-key 가 null/blank 이면 Authorization 헤더 없이도 정상 생성된다")
    void shouldBuildClientWithoutApiKey() {
        // given
        LlmClientProperties properties = new LlmClientProperties(
            "https://api.openai.com",
            "",
            "gpt-4o-mini",
            500, 0.3,
            Duration.ofSeconds(3), Duration.ofSeconds(15)
        );

        // when
        RestClient client = config.llmRestClient(properties);

        // then
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("api-key 가 null 이어도 NPE 없이 정상 생성된다")
    void shouldBuildClientWithNullApiKey() {
        // given
        LlmClientProperties properties = new LlmClientProperties(
            "https://api.openai.com",
            null,
            "gpt-4o-mini",
            500, 0.3,
            Duration.ofSeconds(3), Duration.ofSeconds(15)
        );

        // when
        RestClient client = config.llmRestClient(properties);

        // then
        assertThat(client).isNotNull();
    }
}
