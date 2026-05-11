package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.subscription.HistorySummary;

/**
 * LLM 요약 영속 저장 Port (DB).
 *
 * upsert 의미 — memberId 단위 단일 레코드. 어댑터가 INSERT ... ON DUPLICATE KEY UPDATE
 * 또는 동등 매커니즘으로 항상 최신 fingerprint/summary 만 유지한다.
 */
public interface HistorySummaryCommandPort {

    void persist(HistorySummary summary);
}
