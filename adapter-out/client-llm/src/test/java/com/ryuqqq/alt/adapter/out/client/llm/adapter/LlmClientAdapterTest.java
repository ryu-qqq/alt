package com.ryuqqq.alt.adapter.out.client.llm.adapter;

import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionResponse;
import com.ryuqqq.alt.adapter.out.client.llm.dto.SummaryPayload;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmParseException;
import com.ryuqqq.alt.adapter.out.client.llm.executor.LlmApiExecutor;
import com.ryuqqq.alt.adapter.out.client.llm.mapper.ChatCompletionMapper;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * LlmClientAdapter 단위 테스트.
 *
 * 어댑터 outer try/catch 가 모든 실패 케이스를 LlmSummaryOutcome.unavailable 로 흡수하는지 검증.
 * - HTTP 200 success → success
 * - 4xx / 429 / 5xx / network → unavailable
 * - mapper 의 parsePayload 가 LlmParseException 던지면 → unavailable
 *
 * Executor 는 mock 으로 supplier 를 그대로 실행. ChatCompletionMapper 도 mock — 호출 순서/위임만 검증.
 */
class LlmClientAdapterTest {

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private LlmApiExecutor executor;
    private ChatCompletionMapper mapper;
    private LlmClientAdapter adapter;
    private SubscriptionHistoryReadBundle bundle;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://llm.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        executor = mock(LlmApiExecutor.class);
        mapper = mock(ChatCompletionMapper.class);
        adapter = new LlmClientAdapter(restClient, executor, mapper);
        bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();

        // mapper.toRequest — 항상 같은 요청 반환 (실제 변환은 mapper 단위 테스트에서 검증)
        when(mapper.toRequest(any())).thenReturn(new ChatCompletionRequest(
            "gpt-4o-mini",
            List.of(ChatCompletionRequest.Message.user("test")),
            500, 0.3,
            ChatCompletionRequest.ResponseFormat.jsonObject()
        ));

        // Executor — supplier 그대로 실행
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(executor).execute(any());
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("정상 응답이면 mapper.resolveSummary 결과를 success outcome 으로 반환한다")
        void shouldReturnSuccessOutcome() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                    """
                    {"id":"x","model":"m","choices":[{"index":0,"message":{"role":"assistant","content":"{\\"status\\":\\"PREMIUM\\",\\"narrative\\":\\"현재는 프리미엄 구독 상태입니다.\\"}"},"finish_reason":"stop"}]}
                    """,
                    MediaType.APPLICATION_JSON
                ));
            SummaryPayload payload = new SummaryPayload("PREMIUM", "현재는 프리미엄 구독 상태입니다.");
            when(mapper.parsePayload(any())).thenReturn(payload);
            when(mapper.resolveSummary(payload)).thenReturn("현재는 프리미엄 구독 상태입니다.");

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isTrue();
            assertThat(outcome.summary()).isEqualTo("현재는 프리미엄 구독 상태입니다.");
        }
    }

    @Nested
    @DisplayName("HTTP 오류 분기")
    class HttpErrors {

        @Test
        @DisplayName("4xx 응답이면 unavailable 로 흡수한다")
        void shouldReturnUnavailableOn4xx() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmBadRequestException");
        }

        @Test
        @DisplayName("429 응답이면 LlmRateLimitException 경로로 unavailable 로 흡수한다")
        void shouldReturnUnavailableOnRateLimit() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmRateLimitException");
        }

        @Test
        @DisplayName("5xx 응답이면 unavailable 로 흡수한다")
        void shouldReturnUnavailableOn5xx() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withServerError());

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmServerException");
        }

        @Test
        @DisplayName("network 예외이면 unavailable 로 흡수한다")
        void shouldReturnUnavailableOnNetworkError() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmNetworkException");
        }
    }

    @Nested
    @DisplayName("Mapper 파싱 실패 분기")
    class MapperFailures {

        @Test
        @DisplayName("parsePayload 가 LlmParseException 던지면 unavailable 로 흡수한다")
        void shouldReturnUnavailableWhenParseFails() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withSuccess(
                    "{\"id\":\"x\",\"model\":\"m\",\"choices\":[]}",
                    MediaType.APPLICATION_JSON
                ));
            when(mapper.parsePayload(any())).thenThrow(new LlmParseException("empty choices"));

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmParseException").contains("empty choices");
        }

        @Test
        @DisplayName("resolveSummary 가 예외 던지면 unavailable 로 흡수한다")
        void shouldReturnUnavailableWhenResolveSummaryFails() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://llm.test/chat/completions")))
                .andRespond(withSuccess(
                    "{\"id\":\"x\",\"model\":\"m\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"x\"},\"finish_reason\":\"stop\"}]}",
                    MediaType.APPLICATION_JSON
                ));
            when(mapper.parsePayload(any())).thenReturn(new SummaryPayload("UNKNOWN", "x"));
            when(mapper.resolveSummary(any())).thenThrow(new LlmParseException("unknown status"));

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("LlmParseException");
        }
    }

    @Nested
    @DisplayName("Executor 동작 전파")
    class ExecutorBehavior {

        @Test
        @DisplayName("Executor 가 어떤 예외든 던지면 어댑터가 unavailable 로 흡수한다")
        void shouldAbsorbAnyExceptionFromExecutor() {
            // given
            doAnswer(invocation -> { throw new RuntimeException("circuit broken"); })
                .when(executor).execute(any());

            // when
            LlmSummaryOutcome outcome = adapter.summarize(bundle);

            // then
            assertThat(outcome.isAvailable()).isFalse();
            assertThat(outcome.unavailableReason()).contains("RuntimeException");
        }
    }
}
