package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.member.MemberId;

/**
 * 회원 구독 이력의 LLM 자연어 요약.
 *
 * - fingerprint : 요약이 만들어질 당시 이력의 식별자 (현재 정책: 최신 COMMITTED attemptId).
 *                 새 COMMITTED 시도 발생 시 값이 바뀌어 자동 invalidate.
 * - summary     : LLM 이 생성한 현재 상태 한 줄.
 *
 * 영속 저장소(DB) 가 단일 source-of-truth — 같은 fingerprint 면 LLM 재호출 없이 그대로 사용한다.
 */
public record HistorySummary(
    MemberId memberId,
    long fingerprint,
    String summary
) {

    public static HistorySummary of(MemberId memberId, long fingerprint, String summary) {
        return new HistorySummary(memberId, fingerprint, summary);
    }
}
