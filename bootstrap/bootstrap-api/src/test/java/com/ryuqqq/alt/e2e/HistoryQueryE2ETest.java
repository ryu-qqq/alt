package com.ryuqqq.alt.e2e;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.e2e.support.E2ETestBase;
import com.ryuqqq.alt.e2e.support.E2eFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이력 조회 + LLM 요약 E2E (S-09 ~ S-13).
 *
 * - 회원 / COMMITTED attempt / history_summary 를 SQL 로 직접 시드해 정밀 분기 검증
 * - LlmSummaryClient 는 @MockBean 으로 success/unavailable 분기를 직접 지정
 */
class HistoryQueryE2ETest extends E2ETestBase {

    @Autowired
    private DataSource dataSource;

    private E2eFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new E2eFixtures(jdbc, dataSource);
    }

    @Test
    @DisplayName("S-09: 회원의 COMMITTED 이력이 없으면 summary=null + LLM 미호출")
    void history_emptyCommitted_summaryNull_llmNotCalled() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "NONE");
        // FAILED attempt 만 1건 (COMMITTED 가 없으므로 history 는 비어있어야 한다)
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbc.update(
            "INSERT INTO subscription_attempt(member_id, channel_id, kind, from_status, to_status, requested_at, completed_at, status, failure_reason, idempotency_key, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID, "SUBSCRIBE",
            "NONE", "BASIC",
            now, now,
            "FAILED", "EXTERNAL_SERVER_ERROR",
            fixtures.newIdempotencyKey(),
            now, now
        );

        mockMvc.perform(get("/api/v1/subscriptions/history").param("phoneNumber", phone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.history").isArray())
            .andExpect(jsonPath("$.data.history.length()").value(0))
            .andExpect(jsonPath("$.data.summary").isEmpty());

        verify(llmSummaryClient, never()).summarize(any(SubscriptionHistoryReadBundle.class));
    }

    @Test
    @DisplayName("S-10: COMMITTED 이력이 있고 영속 summary 가 없으면 LLM 호출 + history_summary 영속")
    void history_committedNoSummary_callsLlmAndPersists() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "PREMIUM");
        long attempt1 = fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "NONE", "BASIC", Instant.now().minus(2, ChronoUnit.DAYS));
        long attempt2 = fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "BASIC", "PREMIUM", Instant.now().minus(1, ChronoUnit.DAYS));

        when(llmSummaryClient.summarize(any(SubscriptionHistoryReadBundle.class)))
            .thenReturn(LlmSummaryOutcome.success("최근 BASIC 구독 후 PREMIUM 으로 업그레이드"));

        mockMvc.perform(get("/api/v1/subscriptions/history").param("phoneNumber", phone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.history.length()").value(2))
            .andExpect(jsonPath("$.data.summary").value("최근 BASIC 구독 후 PREMIUM 으로 업그레이드"));

        verify(llmSummaryClient, times(1)).summarize(any(SubscriptionHistoryReadBundle.class));

        // history_summary 영속 확인
        assertThat(fixtures.countHistorySummary(memberId)).isEqualTo(1);
        Map<String, Object> persisted = fixtures.selectHistorySummary(memberId);
        // fingerprint 는 최신 COMMITTED attempt id (attempt2)
        assertThat(((Number) persisted.get("fingerprint")).longValue()).isEqualTo(attempt2);
        assertThat(persisted.get("summary")).isEqualTo("최근 BASIC 구독 후 PREMIUM 으로 업그레이드");
        // attempt1 은 fingerprint 가 아님 (sanity check)
        assertThat(((Number) persisted.get("fingerprint")).longValue()).isNotEqualTo(attempt1);
    }

    @Test
    @DisplayName("S-11: 같은 fingerprint 의 영속 summary 가 있으면 LLM 미호출 + 영속값 그대로 반환")
    void history_persistedSummaryMatchesFingerprint_skipsLlm() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "BASIC");
        long latestAttempt = fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "NONE", "BASIC", Instant.now().minus(1, ChronoUnit.HOURS));
        fixtures.insertHistorySummary(memberId, latestAttempt, "캐시된 요약");

        // stub: 호출되지 않을 거지만 안전하게 설정 — verify 0회로 검출.
        when(llmSummaryClient.summarize(any(SubscriptionHistoryReadBundle.class)))
            .thenReturn(LlmSummaryOutcome.success("호출되면 안 됨"));

        mockMvc.perform(get("/api/v1/subscriptions/history").param("phoneNumber", phone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.history.length()").value(1))
            .andExpect(jsonPath("$.data.summary").value("캐시된 요약"));

        verify(llmSummaryClient, never()).summarize(any(SubscriptionHistoryReadBundle.class));
    }

    @Test
    @DisplayName("S-12: 새 COMMITTED attempt 가 생기면 fingerprint 변경 → LLM 재호출 + history_summary 갱신")
    void history_newAttempt_invalidatesSummary_callsLlmAgain() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "PREMIUM");
        long oldAttempt = fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "NONE", "BASIC", Instant.now().minus(2, ChronoUnit.HOURS));
        fixtures.insertHistorySummary(memberId, oldAttempt, "오래된 요약");
        long newAttempt = fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "BASIC", "PREMIUM", Instant.now().minus(1, ChronoUnit.HOURS));

        when(llmSummaryClient.summarize(any(SubscriptionHistoryReadBundle.class)))
            .thenReturn(LlmSummaryOutcome.success("새 요약"));

        mockMvc.perform(get("/api/v1/subscriptions/history").param("phoneNumber", phone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary").value("새 요약"))
            .andExpect(jsonPath("$.data.history.length()").value(2));

        verify(llmSummaryClient, times(1)).summarize(any(SubscriptionHistoryReadBundle.class));

        // history_summary 가 newAttempt 로 갱신
        Map<String, Object> persisted = fixtures.selectHistorySummary(memberId);
        assertThat(((Number) persisted.get("fingerprint")).longValue()).isEqualTo(newAttempt);
        assertThat(persisted.get("summary")).isEqualTo("새 요약");
    }

    @Test
    @DisplayName("S-13: LLM 호출이 unavailable 이면 summary=null + history_summary 미저장 (graceful degradation)")
    void history_llmUnavailable_summaryNull_notPersisted() throws Exception {
        String phone = fixtures.randomPhoneNumber();
        long memberId = fixtures.insertMember(phone, "BASIC");
        fixtures.insertCommittedAttempt(memberId, E2eFixtures.CHANNEL_HOMEPAGE_ID,
            "SUBSCRIBE", "NONE", "BASIC", Instant.now().minus(1, ChronoUnit.HOURS));

        when(llmSummaryClient.summarize(any(SubscriptionHistoryReadBundle.class)))
            .thenReturn(LlmSummaryOutcome.unavailable("upstream 5xx"));

        mockMvc.perform(get("/api/v1/subscriptions/history").param("phoneNumber", phone))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.history.length()").value(1))
            .andExpect(jsonPath("$.data.summary").isEmpty());

        // history_summary 미저장
        assertThat(fixtures.countHistorySummary(memberId)).isZero();
    }
}
