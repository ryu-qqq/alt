package com.ryuqqq.alt.e2e;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.e2e.support.E2ETestBase;
import com.ryuqqq.alt.e2e.support.E2eFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 멱등성 E2E (S-07).
 *
 * 정책: 같은 Idempotency-Key 재호출은 첫 응답 재반환이 아니라 HTTP 409 (SUB-201) 거절.
 * 두 번째 호출 시 외부 RandomClient 도 호출되지 않아야 한다 (캐시 단락).
 */
class IdempotencyE2ETest extends E2ETestBase {

    @Autowired
    private DataSource dataSource;

    private E2eFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new E2eFixtures(jdbc, dataSource);
    }

    @Test
    @DisplayName("S-07: 동일 Idempotency-Key 재호출은 HTTP 409 SUB-201 로 거절되고 외부 호출은 1회만 일어난다")
    void duplicateIdempotencyKey_returns409Conflict() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();
        when(randomClient.call()).thenReturn(ExternalCallResult.APPROVED);
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.PREMIUM);

        // 1차 호출 — 정상
        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMMITTED"));

        // 2차 호출 — 같은 키 → 409
        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SUB-201"));

        // DB: attempt 1건만, member 1건
        long memberId = fixtures.selectMemberId(phone);
        assertThat(fixtures.countAttempts(memberId)).isEqualTo(1);

        // 외부 호출은 1회만
        verify(randomClient, times(1)).call();
    }
}
