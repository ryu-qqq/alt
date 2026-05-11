package com.ryuqqq.alt.e2e;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.e2e.support.E2ETestBase;
import com.ryuqqq.alt.e2e.support.E2eFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 입력 / 도메인 검증 E2E (S-14 ~ S-15).
 *
 * S-16(NONE→NONE registrationOnly) 는 SubscribeE2ETest 에서 다룸.
 */
class ValidationE2ETest extends E2ETestBase {

    @Autowired
    private DataSource dataSource;

    private E2eFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new E2eFixtures(jdbc, dataSource);
    }

    @Test
    @DisplayName("S-14: 휴대폰 번호 형식이 잘못되면 HTTP 400 + MEM-002 + 외부 호출 미발생")
    void invalidPhoneNumber_returnsBadRequestWithDomainErrorCode() throws Exception {
        SubscribeApiRequest body = new SubscribeApiRequest(
            "abc", E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.BASIC
        );

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("MEM-002"));

        verify(randomClient, never()).call();
    }

    @Test
    @DisplayName("S-15: 존재하지 않는 채널 ID 면 HTTP 404 + CHN-001 + 외부 호출 미발생")
    void channelNotFound_returnsNotFound() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        SubscribeApiRequest body = new SubscribeApiRequest(
            phone, 99999L, SubscriptionStatus.BASIC
        );

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("CHN-001"));

        verify(randomClient, never()).call();
    }
}
