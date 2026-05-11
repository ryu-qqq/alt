package com.ryuqqq.alt.application.subscription.dto.response;

import java.time.Instant;

/**
 * LLM 요약 호출 결과.
 *
 * - summary            : 정상 응답 본문 (실패 / 호출 불필요 시 null)
 * - generatedAt        : 요약이 생성된 시각 (실패 / 호출 불필요 시 null)
 * - stale              : true 면 fingerprint 불일치 + LLM 재호출 실패 → 영속체의 옛 요약을 폴백으로 반환한 케이스
 *                        클라이언트가 "최근 요약이 아닐 수 있음" 안내 가능
 * - unavailableReason  : 실패 사유 (정상 / 호출 불필요 시 null)
 *
 * 정적 팩토리(success / staleSuccess / unavailable / empty) 로만 생성.
 * 모든 외부 호출 실패 (timeout/rate-limit/circuit-open/api-key 미설정 등) 는 unavailable 로 흡수.
 */
public record LlmSummaryOutcome(
    String summary,
    Instant generatedAt,
    boolean stale,
    String unavailableReason
) {

    /**
     * Fresh — fingerprint 일치하는 영속체 또는 방금 생성된 요약.
     */
    public static LlmSummaryOutcome success(String summary, Instant generatedAt) {
        return new LlmSummaryOutcome(summary, generatedAt, false, null);
    }

    /**
     * 테스트 backward compat — generatedAt 검증이 불필요한 기존 테스트 보존용.
     * 운영 코드는 success(String, Instant) 를 사용한다.
     */
    public static LlmSummaryOutcome success(String summary) {
        return new LlmSummaryOutcome(summary, Instant.now(), false, null);
    }

    /**
     * Stale fallback — 새 LLM 호출 실패했지만 영속체에 옛 요약이 있어 폴백.
     * 응답에 stale=true 로 노출, 다음 호출에 재시도 기회는 보존 (DB 미갱신).
     */
    public static LlmSummaryOutcome staleSuccess(String summary, Instant generatedAt) {
        return new LlmSummaryOutcome(summary, generatedAt, true, null);
    }

    /**
     * Unavailable — LLM 호출 실패 + 영속체에 폴백할 요약도 없음.
     */
    public static LlmSummaryOutcome unavailable(String reason) {
        return new LlmSummaryOutcome(null, null, false, reason);
    }

    /**
     * 호출 불필요 — 이력이 비어있어 요약 대상 자체가 없음.
     */
    public static LlmSummaryOutcome empty() {
        return new LlmSummaryOutcome(null, null, false, null);
    }

    public boolean isAvailable() {
        return summary != null;
    }
}
