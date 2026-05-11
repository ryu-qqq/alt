package com.ryuqqq.alt.e2e;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.UnsubscribeApiRequest;
import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.e2e.support.E2ETestBase;
import com.ryuqqq.alt.e2e.support.E2eFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 해지 사가 E2E (S-06a/b/c).
 *
 * 사전 시드: 해지 대상이 되어야 하므로 member.status = PREMIUM 으로 직접 insert 후 해지.
 * 채널: 콜센터 (UNSUBSCRIBE_ONLY).
 */
class UnsubscribeE2ETest extends E2ETestBase {

    @Autowired
    private DataSource dataSource;

    private E2eFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new E2eFixtures(jdbc, dataSource);
    }

    @Test
    @DisplayName("S-06a: PREMIUM 회원이 해지 요청 시 csrng=1 이면 BASIC 으로 강등 + COMMITTED 영속")
    void unsubscribe_happyPath_committed() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "PREMIUM");
        when(randomClient.call()).thenReturn(ExternalCallResult.APPROVED);
        UnsubscribeApiRequest body = new UnsubscribeApiRequest(phone, E2eFixtures.CHANNEL_CALL_CENTER_ID, SubscriptionStatus.BASIC);

        mockMvc.perform(post("/api/v1/subscriptions/unsubscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMMITTED"))
            .andExpect(jsonPath("$.data.currentStatus").value("BASIC"));

        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("BASIC");
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("kind")).isEqualTo("UNSUBSCRIBE");
        assertThat(latest.get("from_status")).isEqualTo("PREMIUM");
        assertThat(latest.get("to_status")).isEqualTo("BASIC");
        assertThat(latest.get("status")).isEqualTo("COMMITTED");

        verify(randomClient, times(1)).call();
    }

    @Test
    @DisplayName("S-06b: PREMIUM 회원 해지 시 csrng=0 이면 ROLLED_BACK 으로 영속되고 status 는 PREMIUM 유지")
    void unsubscribe_externalRejected_rolledBack() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "PREMIUM");
        when(randomClient.call()).thenReturn(ExternalCallResult.REJECTED);
        UnsubscribeApiRequest body = new UnsubscribeApiRequest(phone, E2eFixtures.CHANNEL_CALL_CENTER_ID, SubscriptionStatus.NONE);

        mockMvc.perform(post("/api/v1/subscriptions/unsubscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"))
            .andExpect(jsonPath("$.data.currentStatus").value("PREMIUM"))
            .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_REJECTED"));

        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("PREMIUM");
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("ROLLED_BACK");
    }

    @Test
    @DisplayName("S-06c: PREMIUM 회원 해지 시 csrng 5xx 면 FAILED 로 영속되고 status 는 PREMIUM 유지")
    void unsubscribe_externalServerError_failed() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "PREMIUM");
        when(randomClient.call()).thenThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "http 500"));
        UnsubscribeApiRequest body = new UnsubscribeApiRequest(phone, E2eFixtures.CHANNEL_CALL_CENTER_ID, SubscriptionStatus.NONE);

        mockMvc.perform(post("/api/v1/subscriptions/unsubscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStatus").value("PREMIUM"))
            .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_SERVER_ERROR"));

        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("PREMIUM");
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("FAILED");
        assertThat(latest.get("failure_reason")).isEqualTo("EXTERNAL_SERVER_ERROR");
    }
}
