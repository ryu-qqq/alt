package com.ryuqqq.alt.adapter.out.client.llm.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryuqqq.alt.adapter.out.client.llm.config.LlmClientProperties;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionResponse;
import com.ryuqqq.alt.adapter.out.client.llm.dto.SummaryPayload;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmParseException;
import com.ryuqqq.alt.adapter.out.client.llm.prompt.SubscriptionHistoryPromptBuilder;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatCompletionMapper 단위 테스트.
 *
 * - toRequest      : bundle + properties → ChatCompletionRequest (PromptBuilder 위임)
 * - parsePayload   : ChatCompletionResponse → SummaryPayload (json 역직렬화)
 * - resolveSummary : SummaryPayload → 최종 summary (narrative valid 시 그대로, invalid 시 status fallback)
 */
class ChatCompletionMapperTest {

    private ChatCompletionMapper mapper;
    private LlmClientProperties properties;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        properties = new LlmClientProperties(
            "https://api.openai.com",
            "sk-x",
            "gpt-4o-mini",
            500, 0.3,
            Duration.ofSeconds(3), Duration.ofSeconds(15)
        );
        SubscriptionHistoryPromptBuilder builder = new SubscriptionHistoryPromptBuilder(objectMapper);
        mapper = new ChatCompletionMapper(builder, properties, objectMapper);
    }

    @Nested
    @DisplayName("toRequest")
    class ToRequest {

        @Test
        @DisplayName("properties 의 model/maxTokens/temperature 를 그대로 반영해 요청을 만든다")
        void shouldBuildRequestWithProperties() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();

            // when
            ChatCompletionRequest request = mapper.toRequest(bundle);

            // then
            assertThat(request.model()).isEqualTo("gpt-4o-mini");
            assertThat(request.maxTokens()).isEqualTo(500);
            assertThat(request.temperature()).isEqualTo(0.3);
            assertThat(request.responseFormat()).isNotNull();
            assertThat(request.responseFormat().type()).isEqualTo("json_object");
            assertThat(request.messages()).hasSize(2);
            assertThat(request.messages().get(0).role()).isEqualTo("system");
            assertThat(request.messages().get(1).role()).isEqualTo("user");
        }
    }

    @Nested
    @DisplayName("parsePayload")
    class ParsePayload {

        @Test
        @DisplayName("정상 JSON content 면 SummaryPayload 로 역직렬화한다")
        void shouldParseValidContent() {
            // given
            ChatCompletionResponse response = responseWithContent(
                "{\"status\":\"PREMIUM\",\"narrative\":\"현재는 프리미엄 구독 상태입니다.\"}"
            );

            // when
            SummaryPayload payload = mapper.parsePayload(response);

            // then
            assertThat(payload.status()).isEqualTo("PREMIUM");
            assertThat(payload.narrative()).isEqualTo("현재는 프리미엄 구독 상태입니다.");
        }

        @Test
        @DisplayName("response 가 null 이면 LlmParseException")
        void shouldThrowWhenResponseIsNull() {
            assertThatThrownBy(() -> mapper.parsePayload(null))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("empty choices");
        }

        @Test
        @DisplayName("choices 가 비어있으면 LlmParseException")
        void shouldThrowWhenChoicesEmpty() {
            // given
            ChatCompletionResponse response = new ChatCompletionResponse("x", "m", List.of());

            // when & then
            assertThatThrownBy(() -> mapper.parsePayload(response))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("empty choices");
        }

        @Test
        @DisplayName("message 가 null 이면 LlmParseException")
        void shouldThrowWhenMessageIsNull() {
            // given
            ChatCompletionResponse response = new ChatCompletionResponse(
                "x", "m",
                List.of(new ChatCompletionResponse.Choice(0, null, "stop"))
            );

            // when & then
            assertThatThrownBy(() -> mapper.parsePayload(response))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("missing message content");
        }

        @Test
        @DisplayName("message.content 가 null 이면 LlmParseException")
        void shouldThrowWhenContentIsNull() {
            // given
            ChatCompletionResponse response = responseWithContent(null);

            // when & then
            assertThatThrownBy(() -> mapper.parsePayload(response))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("missing message content");
        }

        @Test
        @DisplayName("content 가 blank 면 LlmParseException")
        void shouldThrowWhenContentBlank() {
            // given
            ChatCompletionResponse response = responseWithContent("   ");

            // when & then
            assertThatThrownBy(() -> mapper.parsePayload(response))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("blank content");
        }

        @Test
        @DisplayName("content 가 깨진 JSON 이면 LlmParseException")
        void shouldThrowWhenContentMalformedJson() {
            // given
            ChatCompletionResponse response = responseWithContent("{not json}");

            // when & then
            assertThatThrownBy(() -> mapper.parsePayload(response))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("malformed inner JSON");
        }
    }

    @Nested
    @DisplayName("resolveSummary")
    class ResolveSummary {

        @Test
        @DisplayName("narrative 가 valid (not blank, 100자 이내) 면 그대로 사용한다")
        void shouldReturnNarrativeWhenValid() {
            // given
            SummaryPayload payload = new SummaryPayload("PREMIUM", "현재는 프리미엄 구독 상태입니다.");

            // when
            String result = mapper.resolveSummary(payload);

            // then
            assertThat(result).isEqualTo("현재는 프리미엄 구독 상태입니다.");
        }

        @Test
        @DisplayName("narrative 가 trim 대상 공백을 가지면 trim 결과를 반환한다")
        void shouldTrimNarrative() {
            // given
            SummaryPayload payload = new SummaryPayload("BASIC", "  hello  ");

            // when
            String result = mapper.resolveSummary(payload);

            // then
            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("narrative 가 null 이면 status 기반 템플릿으로 fallback")
        void shouldFallbackWhenNarrativeNull() {
            // given
            SummaryPayload payload = new SummaryPayload("PREMIUM", null);

            // when
            String result = mapper.resolveSummary(payload);

            // then
            assertThat(result).isEqualTo("현재는 프리미엄 구독 상태입니다.");
        }

        @Test
        @DisplayName("narrative 가 blank 면 status 기반 템플릿으로 fallback")
        void shouldFallbackWhenNarrativeBlank() {
            // given
            SummaryPayload payload = new SummaryPayload("BASIC", "   ");

            // when
            String result = mapper.resolveSummary(payload);

            // then
            assertThat(result).isEqualTo("현재는 일반 구독 상태입니다.");
        }

        @Test
        @DisplayName("narrative 가 100자 초과면 status 기반 템플릿으로 fallback")
        void shouldFallbackWhenNarrativeTooLong() {
            // given — 101자
            String longNarrative = "가".repeat(101);
            SummaryPayload payload = new SummaryPayload("NONE", longNarrative);

            // when
            String result = mapper.resolveSummary(payload);

            // then
            assertThat(result).isEqualTo("현재는 구독 안함 상태입니다.");
        }

        @Test
        @DisplayName("status 가 소문자/공백 포함이어도 정상 정규화한다")
        void shouldNormalizeStatus() {
            // given
            SummaryPayload payload = new SummaryPayload("  premium  ", null);

            // when
            String result = mapper.resolveSummary(payload);

            // then — fallback 메시지 포함 PREMIUM
            assertThat(result).isEqualTo("현재는 프리미엄 구독 상태입니다.");
        }

        @Test
        @DisplayName("status 가 null 이면 LlmParseException")
        void shouldThrowWhenStatusNull() {
            // given
            SummaryPayload payload = new SummaryPayload(null, "x");

            // when & then
            assertThatThrownBy(() -> mapper.resolveSummary(payload))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("missing status field");
        }

        @Test
        @DisplayName("status 가 blank 면 LlmParseException")
        void shouldThrowWhenStatusBlank() {
            // given
            SummaryPayload payload = new SummaryPayload("   ", "x");

            // when & then
            assertThatThrownBy(() -> mapper.resolveSummary(payload))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("missing status field");
        }

        @Test
        @DisplayName("status 가 알 수 없는 값이면 LlmParseException")
        void shouldThrowWhenStatusUnknown() {
            // given
            SummaryPayload payload = new SummaryPayload("UNKNOWN_STATUS", "x");

            // when & then
            assertThatThrownBy(() -> mapper.resolveSummary(payload))
                .isInstanceOf(LlmParseException.class)
                .hasMessageContaining("unknown status value");
        }
    }

    private static ChatCompletionResponse responseWithContent(String content) {
        return new ChatCompletionResponse(
            "x", "m",
            List.of(new ChatCompletionResponse.Choice(
                0,
                new ChatCompletionResponse.Message("assistant", content),
                "stop"
            ))
        );
    }
}
