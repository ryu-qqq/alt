package com.ryuqqq.alt.adapter.in.subscription.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.alt.adapter.in.common.error.ErrorMapperRegistry;
import com.ryuqqq.alt.adapter.in.common.error.GlobalExceptionHandler;
import com.ryuqqq.alt.adapter.in.subscription.error.SubscriptionErrorMapper;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.port.in.SubscribeUseCase;
import com.ryuqqq.alt.application.subscription.port.in.UnsubscribeUseCase;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SubscriptionCommandController 슬라이스 테스트.
 * UseCase 는 Mock, GlobalExceptionHandler + ErrorMapperRegistry + SubscriptionErrorMapper 만 import 하여
 * 컨트롤러 + 에러 매핑까지 통합 검증.
 *
 * Idempotency-Key 는 컨트롤러에서 필수(@RequestHeader required=true). 모든 정상 시나리오는
 * 기본 키를 헤더로 자동 추가하는 헬퍼(postSubscribe / postUnsubscribe)를 통해 호출한다.
 */
@WebMvcTest(controllers = SubscriptionCommandController.class)
@Import({GlobalExceptionHandler.class, ErrorMapperRegistry.class, SubscriptionErrorMapper.class})
@DisplayName("SubscriptionCommandController 통합 검증")
class SubscriptionCommandControllerTest {

    private static final String SUBSCRIBE_PATH = "/api/v1/subscriptions/subscribe";
    private static final String UNSUBSCRIBE_PATH = "/api/v1/subscriptions/unsubscribe";
    private static final String VALID_PHONE = "01012345678";
    private static final Long CHANNEL_ID = 7L;
    private static final String DEFAULT_IDEMPOTENCY_KEY = "test-idem-key-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscribeUseCase subscribeUseCase;

    @MockitoBean
    private UnsubscribeUseCase unsubscribeUseCase;

    private static Map<String, Object> subscribeBody(String phone, Long channelId, String targetStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("phoneNumber", phone);
        body.put("channelId", channelId);
        body.put("targetStatus", targetStatus);
        return body;
    }

    /** 기본 헤더(Idempotency-Key) 가 자동 추가되는 POST 빌더. 헤더 누락 시나리오는 직접 post(...) 호출. */
    private MockHttpServletRequestBuilder postSubscribe(Map<String, Object> body) throws Exception {
        return post(SUBSCRIBE_PATH)
            .header("Idempotency-Key", DEFAULT_IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder postUnsubscribe(Map<String, Object> body) throws Exception {
        return post(UNSUBSCRIBE_PATH)
            .header("Idempotency-Key", DEFAULT_IDEMPOTENCY_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions/subscribe")
    class Subscribe {

        @Nested
        @DisplayName("해피 패스")
        class HappyPath {

            @Test
            @DisplayName("정상 요청이면 200 + ApiResponse 래퍼로 결과를 반환한다")
            void shouldReturn200WhenValidRequest() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class))).willReturn(
                    new SubscribeResult(42L, AttemptStatus.COMMITTED, SubscriptionStatus.PREMIUM, null)
                );

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "PREMIUM")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.attemptId").value(42))
                    .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                    .andExpect(jsonPath("$.data.currentStatus").value("PREMIUM"))
                    .andExpect(jsonPath("$.data.failureReason").doesNotExist());
            }

            @Test
            @DisplayName("registrationOnly 결과는 attemptId / status 가 null 로 응답된다")
            void shouldReturnNullAttemptIdWhenRegistrationOnly() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willReturn(SubscribeResult.registrationOnly(SubscriptionStatus.NONE));

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "NONE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.attemptId").doesNotExist())
                    .andExpect(jsonPath("$.data.status").doesNotExist())
                    .andExpect(jsonPath("$.data.currentStatus").value("NONE"));
            }
        }

        @Nested
        @DisplayName("Idempotency-Key 헤더")
        class IdempotencyHeader {

            @Test
            @DisplayName("헤더 누락 시 400 + MISSING_HEADER")
            void shouldReturn400WhenIdempotencyHeaderMissing() throws Exception {
                mockMvc.perform(post(SUBSCRIBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
            }

            @Test
            @DisplayName("Idempotency-Key 헤더 값이 command 로 전달된다")
            void shouldPassIdempotencyKeyHeaderToCommand() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class))).willReturn(
                    new SubscribeResult(1L, AttemptStatus.COMMITTED, SubscriptionStatus.BASIC, null)
                );

                mockMvc.perform(post(SUBSCRIBE_PATH)
                        .header("Idempotency-Key", "client-key-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC"))))
                    .andExpect(status().isOk());

                ArgumentCaptor<SubscribeCommand> captor = ArgumentCaptor.forClass(SubscribeCommand.class);
                verify(subscribeUseCase).execute(captor.capture());
                assertThat(captor.getValue().idempotencyKey()).isEqualTo("client-key-1234");
            }
        }

        @Nested
        @DisplayName("검증 실패")
        class ValidationFail {

            @Test
            @DisplayName("phoneNumber 가 null 이면 400 + errors.phoneNumber")
            void shouldReturn400WhenPhoneNumberIsNull() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody(null, CHANNEL_ID, "BASIC")))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("x-error-code", "VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.phoneNumber").exists());
            }

            @Test
            @DisplayName("phoneNumber 가 blank 이면 400")
            void shouldReturn400WhenPhoneNumberIsBlank() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody("   ", CHANNEL_ID, "BASIC")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phoneNumber").exists());
            }

            @Test
            @DisplayName("channelId 가 null 이면 400 + errors.channelId")
            void shouldReturn400WhenChannelIdIsNull() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, null, "BASIC")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.channelId").exists());
            }

            @Test
            @DisplayName("channelId 가 0 이하이면 400 + errors.channelId")
            void shouldReturn400WhenChannelIdIsNotPositive() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, -1L, "BASIC")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.channelId").exists());
            }

            @Test
            @DisplayName("targetStatus 가 null 이면 400 + errors.targetStatus")
            void shouldReturn400WhenTargetStatusIsNull() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.targetStatus").exists());
            }

            @Test
            @DisplayName("targetStatus 가 enum 에 없는 값이면 400 + MALFORMED_REQUEST")
            void shouldReturn400WhenTargetStatusIsUnknownEnum() throws Exception {
                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "UNKNOWN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            }

            @Test
            @DisplayName("요청 본문이 잘못된 JSON 이면 400 + MALFORMED_REQUEST")
            void shouldReturn400WhenBodyIsInvalidJson() throws Exception {
                mockMvc.perform(post(SUBSCRIBE_PATH)
                        .header("Idempotency-Key", DEFAULT_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                    .andExpect(header().string("x-error-code", "MALFORMED_REQUEST"));
            }

            @Test
            @DisplayName("요청 본문이 비어있으면 400 + MALFORMED_REQUEST")
            void shouldReturn400WhenBodyIsEmpty() throws Exception {
                mockMvc.perform(post(SUBSCRIBE_PATH)
                        .header("Idempotency-Key", DEFAULT_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
            }
        }

        @Nested
        @DisplayName("도메인 예외 전파")
        class DomainExceptionPropagation {

            @Test
            @DisplayName("MemberNotFoundException → 404 + MEM-001")
            void shouldReturn404WhenMemberNotFound() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willThrow(new MemberNotFoundException("01099999999"));

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC")))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("x-error-code", "MEM-001"))
                    .andExpect(jsonPath("$.code").value("MEM-001"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.traceId").exists());
            }

            @Test
            @DisplayName("ChannelNotFoundException → 404 + CHN-001")
            void shouldReturn404WhenChannelNotFound() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willThrow(new ChannelNotFoundException("id=7"));

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CHN-001"));
            }

            @Test
            @DisplayName("InvalidSubscribeTransitionException → 403 + SUB-001")
            void shouldReturn403WhenInvalidTransition() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willThrow(new InvalidSubscribeTransitionException("PREMIUM -> BASIC"));

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SUB-001"));
            }

            @Test
            @DisplayName("ChannelSubscribeNotAllowedException → 403 + CHN-002")
            void shouldReturn403WhenChannelDoesNotAllowSubscribe() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willThrow(new ChannelSubscribeNotAllowedException("UNSUBSCRIBE_ONLY"));

                mockMvc.perform(postSubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CHN-002"));
            }

            @Test
            @DisplayName("IdempotencyConflictException → 409 + SUB-201")
            void shouldReturn409WhenIdempotencyConflict() throws Exception {
                given(subscribeUseCase.execute(any(SubscribeCommand.class)))
                    .willThrow(new IdempotencyConflictException("dup-key"));

                mockMvc.perform(post(SUBSCRIBE_PATH)
                        .header("Idempotency-Key", "dup-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            subscribeBody(VALID_PHONE, CHANNEL_ID, "BASIC"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SUB-201"));
            }
        }

        @Nested
        @DisplayName("잘못된 메서드 / 경로")
        class WrongMethodOrPath {

            @Test
            @DisplayName("GET /subscribe 는 405")
            void shouldReturn405WhenGetSubscribe() throws Exception {
                mockMvc.perform(get(SUBSCRIBE_PATH))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
            }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/subscriptions/unsubscribe")
    class Unsubscribe {

        @Nested
        @DisplayName("해피 패스")
        class HappyPath {

            @Test
            @DisplayName("정상 요청이면 200 + ApiResponse 래퍼로 결과를 반환한다")
            void shouldReturn200WhenValidRequest() throws Exception {
                given(unsubscribeUseCase.execute(any(UnsubscribeCommand.class))).willReturn(
                    new UnsubscribeResult(99L, AttemptStatus.COMMITTED, SubscriptionStatus.NONE, null)
                );

                mockMvc.perform(postUnsubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "NONE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.attemptId").value(99))
                    .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                    .andExpect(jsonPath("$.data.currentStatus").value("NONE"));
            }

            @Test
            @DisplayName("FAILED 결과는 failureReason 이 응답에 포함된다")
            void shouldReturnFailureReasonWhenFailed() throws Exception {
                given(unsubscribeUseCase.execute(any(UnsubscribeCommand.class))).willReturn(
                    new UnsubscribeResult(1L, AttemptStatus.FAILED, SubscriptionStatus.BASIC, "EXTERNAL_TIMEOUT")
                );

                mockMvc.perform(postUnsubscribe(subscribeBody(VALID_PHONE, CHANNEL_ID, "NONE")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_TIMEOUT"));
            }
        }

        @Nested
        @DisplayName("Idempotency-Key 헤더")
        class IdempotencyHeader {

            @Test
            @DisplayName("헤더 누락 시 400 + MISSING_HEADER")
            void shouldReturn400WhenIdempotencyHeaderMissing() throws Exception {
                mockMvc.perform(post(UNSUBSCRIBE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            subscribeBody(VALID_PHONE, CHANNEL_ID, "NONE"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
            }

            @Test
            @DisplayName("헤더 포함 시 UnsubscribeCommand.idempotencyKey 에 전달된다")
            void shouldPassIdempotencyKeyHeader() throws Exception {
                given(unsubscribeUseCase.execute(any(UnsubscribeCommand.class))).willReturn(
                    new UnsubscribeResult(1L, AttemptStatus.COMMITTED, SubscriptionStatus.NONE, null)
                );

                mockMvc.perform(post(UNSUBSCRIBE_PATH)
                        .header("Idempotency-Key", "unsub-key-99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                            subscribeBody(VALID_PHONE, CHANNEL_ID, "NONE"))))
                    .andExpect(status().isOk());

                ArgumentCaptor<UnsubscribeCommand> captor = ArgumentCaptor.forClass(UnsubscribeCommand.class);
                verify(unsubscribeUseCase).execute(captor.capture());
                assertThat(captor.getValue().idempotencyKey()).isEqualTo("unsub-key-99");
            }
        }

        @Nested
        @DisplayName("검증 실패")
        class ValidationFail {

            @Test
            @DisplayName("phoneNumber 누락이면 400 + errors.phoneNumber")
            void shouldReturn400WhenPhoneNumberIsBlank() throws Exception {
                mockMvc.perform(postUnsubscribe(subscribeBody("", CHANNEL_ID, "NONE")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phoneNumber").exists());
            }

            @Test
            @DisplayName("channelId 가 음수면 400 + errors.channelId")
            void shouldReturn400WhenChannelIdNotPositive() throws Exception {
                mockMvc.perform(postUnsubscribe(subscribeBody(VALID_PHONE, -5L, "NONE")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.channelId").exists());
            }
        }
    }
}
