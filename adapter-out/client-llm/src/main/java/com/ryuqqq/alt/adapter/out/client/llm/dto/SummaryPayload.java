package com.ryuqqq.alt.adapter.out.client.llm.dto;

/**
 * LLM 이 반환하는 inner JSON 페이로드 (하이브리드 스키마).
 *
 * 스키마: {"status": "NONE|BASIC|PREMIUM", "narrative": "한 줄 한국어"}
 *
 * - status    : SubscriptionStatus enum 값 (NONE/BASIC/PREMIUM). LLM 의 추측 제거 — 강한 일관성 보장.
 * - narrative : 자연어 한 줄. valid 하면 그대로 사용, invalid (blank/길이초과/null) 면 어댑터가 status 기반 템플릿으로 fallback.
 *
 * 이력 items 는 우리 DB 가 단일 진실 — LLM 은 status 분류 + 자연어 표현 한 줄만 책임진다.
 */
public record SummaryPayload(String status, String narrative) {
}
