package com.ryuqqq.alt.adapter.out.client.csrng.adapter;

import com.ryuqqq.alt.adapter.out.client.csrng.executor.CsrngApiExecutor;
import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * CsrngClientAdapter 단위 테스트.
 *
 * RestClient HTTP 호출 분기는 MockRestServiceServer 로 시뮬레이션,
 * Executor 는 Mockito mock 으로 실행 흐름만 보존(실제 supplier 위임).
 *
 * CsrngApiExecutor 는 Resilience4j 동작을 의도적으로 우회 — 어댑터의 try/catch 분기 자체만 검증한다.
 */
class CsrngClientAdapterTest {

    private RestClient restClient;
    private MockRestServiceServer mockServer;
    private CsrngApiExecutor executor;
    private CsrngClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://csrng.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        executor = mock(CsrngApiExecutor.class);
        adapter = new CsrngClientAdapter(restClient, executor);

        // Executor 는 supplier 를 그대로 실행 (CB/Retry 우회).
        // doAnswer 사용 — 테스트 메서드에서 doThrow 로 재정의 가능.
        doAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(executor).execute(any());
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("random=1 응답이면 APPROVED 를 반환한다")
        void shouldReturnApprovedWhenRandomIsOne() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(queryParam("min", "0"))
                .andExpect(queryParam("max", "1"))
                .andRespond(withSuccess(
                    "[{\"status\":\"success\",\"min\":0,\"max\":1,\"random\":1}]",
                    MediaType.APPLICATION_JSON
                ));

            // when
            ExternalCallResult result = adapter.call();

            // then
            assertThat(result).isEqualTo(ExternalCallResult.APPROVED);
            mockServer.verify();
        }

        @Test
        @DisplayName("random=0 응답이면 REJECTED 를 반환한다")
        void shouldReturnRejectedWhenRandomIsZero() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess(
                    "[{\"status\":\"success\",\"min\":0,\"max\":1,\"random\":0}]",
                    MediaType.APPLICATION_JSON
                ));

            // when
            ExternalCallResult result = adapter.call();

            // then
            assertThat(result).isEqualTo(ExternalCallResult.REJECTED);
        }

        @Test
        @DisplayName("status 가 대소문자 혼합인 success 여도 정상 처리한다")
        void shouldHandleCaseInsensitiveStatusSuccess() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess(
                    "[{\"status\":\"SUCCESS\",\"min\":0,\"max\":1,\"random\":1}]",
                    MediaType.APPLICATION_JSON
                ));

            // when
            ExternalCallResult result = adapter.call();

            // then
            assertThat(result).isEqualTo(ExternalCallResult.APPROVED);
        }
    }

    @Nested
    @DisplayName("HTTP 오류 분기")
    class HttpErrors {

        @Test
        @DisplayName("5xx 응답이면 RandomClientException(EXTERNAL_SERVER_ERROR) 으로 번역한다")
        void shouldTranslate5xxToServerError() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withServerError());

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("4xx 응답이면 RandomClientException(EXTERNAL_CLIENT_ERROR) 으로 번역한다")
        void shouldTranslate4xxToClientError() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_CLIENT_ERROR);
        }

        @Test
        @DisplayName("network/timeout 예외이면 RandomClientException(EXTERNAL_TIMEOUT) 으로 번역한다")
        void shouldTranslateNetworkErrorToTimeout() {
            // given — withException 은 ResourceAccessException 으로 래핑돼서 전파된다
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("응답 파싱 실패 분기")
    class ParseFailures {

        @Test
        @DisplayName("빈 JSON 배열이면 EXTERNAL_PARSE_FAILURE 로 번역한다")
        void shouldTranslateEmptyArrayToParseFailure() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_PARSE_FAILURE);
        }

        @Test
        @DisplayName("status 가 success 가 아니면 EXTERNAL_PARSE_FAILURE 로 번역한다")
        void shouldTranslateNonSuccessStatusToParseFailure() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess(
                    "[{\"status\":\"failure\",\"min\":0,\"max\":1,\"random\":1}]",
                    MediaType.APPLICATION_JSON
                ));

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_PARSE_FAILURE);
        }

        @Test
        @DisplayName("random 이 0/1 이 아닌 값(예: 99) 이면 EXTERNAL_PARSE_FAILURE 로 번역한다")
        void shouldTranslateUnexpectedRandomToParseFailure() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess(
                    "[{\"status\":\"success\",\"min\":0,\"max\":1,\"random\":99}]",
                    MediaType.APPLICATION_JSON
                ));

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_PARSE_FAILURE);
        }
    }

    @Nested
    @DisplayName("Executor 동작 전파")
    class ExecutorBehavior {

        @Test
        @DisplayName("Executor 가 CB OPEN 으로 RandomClientException 을 던지면 그대로 propagate")
        void shouldPropagateRandomClientExceptionFromExecutor() {
            // given
            RandomClientException circuitOpen = new RandomClientException(
                AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN, "csrng circuit breaker is open"
            );
            // executor 가 supplier 를 실행하지 않고 즉시 예외 (setUp 의 doAnswer 를 재정의)
            doThrow(circuitOpen).when(executor).execute(any());

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isSameAs(circuitOpen);
        }

        @Test
        @DisplayName("알 수 없는 RuntimeException 이면 EXTERNAL_UNKNOWN 으로 흡수한다")
        void shouldTranslateUnknownExceptionToUnknown() {
            // given
            doThrow(new RuntimeException("boom")).when(executor).execute(any());

            // when & then
            assertThatThrownBy(() -> adapter.call())
                .isInstanceOf(RandomClientException.class)
                .extracting(e -> ((RandomClientException) e).reason())
                .isEqualTo(AttemptFailureReason.EXTERNAL_UNKNOWN);
        }

        @Test
        @DisplayName("Executor 가 supplier 를 정확히 1회 호출한다")
        void shouldInvokeExecutorOnce() {
            // given
            mockServer.expect(requestTo(Matchers.startsWith("https://csrng.test")))
                .andRespond(withSuccess(
                    "[{\"status\":\"success\",\"min\":0,\"max\":1,\"random\":1}]",
                    MediaType.APPLICATION_JSON
                ));

            // when
            adapter.call();

            // then — Executor.execute(supplier) 가 1회 호출됨
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Supplier<ExternalCallResult>> captor = ArgumentCaptor.forClass(Supplier.class);
            verify(executor).execute(captor.capture());
            assertThat(captor.getValue()).isNotNull();
        }
    }
}
