package com.ryuqqq.alt.e2e;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
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
 * 구독 사가 E2E (S-01 ~ S-05, S-08).
 *
 * 외부 csrng 호출은 RandomClient @MockBean 으로 결과 분기를 직접 지정한다.
 * - APPROVED → COMMITTED + member.status 갱신
 * - REJECTED → ROLLED_BACK + member.status 유지
 * - 예외 throw → FAILED + 해당 failure_reason
 */
class SubscribeE2ETest extends E2ETestBase {

    @Autowired
    private DataSource dataSource;

    private E2eFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new E2eFixtures(jdbc, dataSource);
    }

    @Test
    @DisplayName("S-01: csrng=1 응답이면 PREMIUM 구독이 COMMITTED 로 영속되고 member status 가 갱신된다")
    void subscribe_happyPath_committed() throws Exception {
        // given
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();
        when(randomClient.call()).thenReturn(ExternalCallResult.APPROVED);
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.PREMIUM);

        // when & then
        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMMITTED"))
            .andExpect(jsonPath("$.data.currentStatus").value("PREMIUM"))
            .andExpect(jsonPath("$.data.failureReason").isEmpty());
        // NOTE: data.attemptId 는 현재 application 코드(SubscribeResult.from)에서 null 로 직렬화된다.
        // attempt 가 DB 에 COMMITTED 로 영속되는 것은 아래 DB 검증으로 갈음. (application 코드 의문점 #1)

        // DB 검증
        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("PREMIUM");
        long memberId = fixtures.selectMemberId(phone);
        assertThat(fixtures.countAttempts(memberId)).isEqualTo(1);
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("COMMITTED");
        assertThat(latest.get("kind")).isEqualTo("SUBSCRIBE");
        assertThat(latest.get("from_status")).isEqualTo("NONE");
        assertThat(latest.get("to_status")).isEqualTo("PREMIUM");

        verify(randomClient, times(1)).call();
    }

    @Test
    @DisplayName("S-02: csrng=0 응답이면 ROLLED_BACK 으로 영속되고 member status 는 NONE 으로 유지된다")
    void subscribe_externalRejected_rolledBack() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();
        when(randomClient.call()).thenReturn(ExternalCallResult.REJECTED);
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.BASIC);

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"))
            .andExpect(jsonPath("$.data.currentStatus").value("NONE"))
            .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_REJECTED"));

        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("NONE");
        long memberId = fixtures.selectMemberId(phone);
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("ROLLED_BACK");
        assertThat(latest.get("failure_reason")).isEqualTo("EXTERNAL_REJECTED");
    }

    @Test
    @DisplayName("S-03: csrng 5xx 실패면 FAILED + EXTERNAL_SERVER_ERROR 로 흡수되고 member status 는 NONE 유지")
    void subscribe_externalServerError_failed() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();
        when(randomClient.call()).thenThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "http 500"));
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.BASIC);

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.currentStatus").value("NONE"))
            .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_SERVER_ERROR"));

        long memberId = fixtures.selectMemberId(phone);
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("FAILED");
        assertThat(latest.get("failure_reason")).isEqualTo("EXTERNAL_SERVER_ERROR");
        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("NONE");
    }

    @Test
    @DisplayName("S-04: csrng 타임아웃이면 FAILED + EXTERNAL_TIMEOUT 로 영속된다")
    void subscribe_externalTimeout_failed() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();
        when(randomClient.call()).thenThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_TIMEOUT, "read timeout"));
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.PREMIUM);

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.failureReason").value("EXTERNAL_TIMEOUT"));
    }

    @Test
    @DisplayName("S-05: 신규 회원이라도 외부 호출 실패시 member 는 별도 트랜잭션으로 영속되고 attempt 만 FAILED 로 기록된다")
    void subscribe_newMember_externalFails_memberStillCommitted() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        String idempotencyKey = fixtures.newIdempotencyKey();

        when(randomClient.call()).thenThrow(new RandomClientException(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "http 502"));
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.BASIC);

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"));

        // member 는 commit (status=NONE 신규 가입 상태)
        assertThat(fixtures.countMembers(phone)).isEqualTo(1);
        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("NONE");

        // attempt 는 FAILED 1건
        long memberId = fixtures.selectMemberId(phone);
        assertThat(fixtures.countAttempts(memberId)).isEqualTo(1);
        Map<String, Object> latest = fixtures.selectLatestAttempt(memberId);
        assertThat(latest.get("status")).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("S-08: 다른 Idempotency-Key 로 두 번 호출하면 각각 정상 처리되어 시도 2건이 영속된다")
    void subscribe_differentIdempotencyKeys_bothProcessed() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        when(randomClient.call()).thenReturn(ExternalCallResult.APPROVED);

        // 1차: NONE → BASIC
        SubscribeApiRequest firstBody = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.BASIC);
        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(firstBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMMITTED"))
            .andExpect(jsonPath("$.data.currentStatus").value("BASIC"));

        // 2차: BASIC → PREMIUM (정책상 가능)
        SubscribeApiRequest secondBody = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.PREMIUM);
        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(secondBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMMITTED"))
            .andExpect(jsonPath("$.data.currentStatus").value("PREMIUM"));

        // DB: member 1건, attempt 2건
        assertThat(fixtures.countMembers(phone)).isEqualTo(1);
        long memberId = fixtures.selectMemberId(phone);
        assertThat(fixtures.countAttempts(memberId)).isEqualTo(2);
        assertThat(fixtures.selectMemberStatus(phone)).isEqualTo("PREMIUM");

        verify(randomClient, times(2)).call();
    }

    @Test
    @DisplayName("S-16: targetStatus=NONE 신규 가입은 회원 등록만 수행하고 시도 없이 currentStatus=NONE 으로 응답한다")
    void subscribe_targetNoneRegistrationOnly() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        SubscribeApiRequest body = new SubscribeApiRequest(phone, E2eFixtures.CHANNEL_HOMEPAGE_ID, SubscriptionStatus.NONE);

        mockMvc.perform(post("/api/v1/subscriptions/subscribe")
                .header("Idempotency-Key", fixtures.newIdempotencyKey())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.attemptId").isEmpty())
            .andExpect(jsonPath("$.data.status").isEmpty())
            .andExpect(jsonPath("$.data.currentStatus").value("NONE"));

        // member 만 영속, attempt 없음
        assertThat(fixtures.countMembers(phone)).isEqualTo(1);
        long memberId = fixtures.selectMemberId(phone);
        assertThat(fixtures.countAttempts(memberId)).isEqualTo(0);

        // 외부 호출은 일어나지 않음
        verify(randomClient, times(0)).call();
    }
}
