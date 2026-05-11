package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;

import java.util.Optional;

/**
 * LLM 요약 영속 조회 Port (DB).
 *
 * fingerprint 비교로 LLM 호출 회피 — 영속 summary 가 현재 이력과 일치하면 그대로 재사용.
 */
public interface HistorySummaryQueryPort {

    Optional<HistorySummary> find(MemberId memberId);
}
