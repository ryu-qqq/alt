package com.ryuqqq.alt.adapter.in.subscription.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ryuqqq.alt.adapter.in.common.error.ErrorMapperRegistry;
import com.ryuqqq.alt.adapter.in.common.error.GlobalExceptionHandler;
import com.ryuqqq.alt.adapter.in.subscription.error.SubscriptionErrorMapper;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.application.subscription.port.in.QuerySubscriptionHistoryUseCase;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SubscriptionQueryController 슬라이스 테스트.
 */
@WebMvcTest(controllers = SubscriptionQueryController.class)
@Import({GlobalExceptionHandler.class, ErrorMapperRegistry.class, SubscriptionErrorMapper.class})
@DisplayName("SubscriptionQueryController 통합 검증")
class SubscriptionQueryControllerTest {

    private static final String HISTORY_PATH = "/api/v1/subscriptions/history";
    private static final String VALID_PHONE = "01012345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuerySubscriptionHistoryUseCase querySubscriptionHistoryUseCase;

    @Nested
    @DisplayName("GET /api/v1/subscriptions/history")
    class GetHistory {

        @Nested
        @DisplayName("해피 패스")
        class HappyPath {

            @Test
            @DisplayName("이력이 있으면 200 + history + summary 응답")
            void shouldReturn200WithHistoryAndSummary() throws Exception {
                Instant occurredAt = Instant.parse("2026-05-01T10:00:00Z");
                SubscriptionHistoryItemView item = new SubscriptionHistoryItemView(
                    1L, 7L, "test-channel",
                    AttemptKind.SUBSCRIBE,
                    SubscriptionStatus.NONE,
                    SubscriptionStatus.BASIC,
                    occurredAt
                );
                given(querySubscriptionHistoryUseCase.execute(any(QuerySubscriptionHistoryQuery.class)))
                    .willReturn(QuerySubscriptionHistoryResult.of(List.of(item), "총 1건의 구독 이력이 있습니다"));

                mockMvc.perform(get(HISTORY_PATH).param("phoneNumber", VALID_PHONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary").value("총 1건의 구독 이력이 있습니다"))
                    .andExpect(jsonPath("$.data.history.length()").value(1))
                    .andExpect(jsonPath("$.data.history[0].attemptId").value(1))
                    .andExpect(jsonPath("$.data.history[0].channelId").value(7))
                    .andExpect(jsonPath("$.data.history[0].channelName").value("test-channel"))
                    .andExpect(jsonPath("$.data.history[0].kind").value("SUBSCRIBE"))
                    .andExpect(jsonPath("$.data.history[0].fromStatus").value("NONE"))
                    .andExpect(jsonPath("$.data.history[0].toStatus").value("BASIC"))
                    .andExpect(jsonPath("$.data.history[0].occurredAt").exists());
            }

            @Test
            @DisplayName("이력이 없으면 200 + 빈 history, summary null 응답")
            void shouldReturn200WithEmptyHistory() throws Exception {
                given(querySubscriptionHistoryUseCase.execute(any(QuerySubscriptionHistoryQuery.class)))
                    .willReturn(QuerySubscriptionHistoryResult.withoutSummary(List.of()));

                mockMvc.perform(get(HISTORY_PATH).param("phoneNumber", VALID_PHONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.history.length()").value(0))
                    .andExpect(jsonPath("$.data.summary").doesNotExist());
            }

            @Test
            @DisplayName("LLM 실패로 summary 가 null 이어도 history 만 응답에 포함된다")
            void shouldReturn200WhenSummaryIsNull() throws Exception {
                Instant occurredAt = Instant.parse("2026-05-01T10:00:00Z");
                SubscriptionHistoryItemView item = new SubscriptionHistoryItemView(
                    1L, 7L, "ch",
                    AttemptKind.SUBSCRIBE,
                    SubscriptionStatus.NONE,
                    SubscriptionStatus.PREMIUM,
                    occurredAt
                );
                given(querySubscriptionHistoryUseCase.execute(any(QuerySubscriptionHistoryQuery.class)))
                    .willReturn(QuerySubscriptionHistoryResult.withoutSummary(List.of(item)));

                mockMvc.perform(get(HISTORY_PATH).param("phoneNumber", VALID_PHONE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.history.length()").value(1))
                    .andExpect(jsonPath("$.data.summary").doesNotExist());
            }
        }

        @Nested
        @DisplayName("검증 실패")
        class ValidationFail {

            @Test
            @DisplayName("phoneNumber 쿼리 파라미터가 누락되면 400 + errors.phoneNumber")
            void shouldReturn400WhenPhoneNumberMissing() throws Exception {
                mockMvc.perform(get(HISTORY_PATH))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("x-error-code", "VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.phoneNumber").exists());
            }

            @Test
            @DisplayName("phoneNumber 가 빈 문자열이면 400")
            void shouldReturn400WhenPhoneNumberIsBlank() throws Exception {
                mockMvc.perform(get(HISTORY_PATH).param("phoneNumber", ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.phoneNumber").exists());
            }
        }

        @Nested
        @DisplayName("도메인 예외 전파")
        class DomainExceptionPropagation {

            @Test
            @DisplayName("MemberNotFoundException → 404 + MEM-001")
            void shouldReturn404WhenMemberNotFound() throws Exception {
                given(querySubscriptionHistoryUseCase.execute(any(QuerySubscriptionHistoryQuery.class)))
                    .willThrow(new MemberNotFoundException("01099999999"));

                mockMvc.perform(get(HISTORY_PATH).param("phoneNumber", VALID_PHONE))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string("x-error-code", "MEM-001"))
                    .andExpect(jsonPath("$.code").value("MEM-001"))
                    .andExpect(jsonPath("$.traceId").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
            }
        }

        @Nested
        @DisplayName("잘못된 메서드")
        class WrongMethod {

            @Test
            @DisplayName("POST /history 는 405")
            void shouldReturn405WhenPostHistory() throws Exception {
                mockMvc.perform(post(HISTORY_PATH))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
            }
        }
    }
}
