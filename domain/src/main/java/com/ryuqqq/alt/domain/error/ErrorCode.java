package com.ryuqqq.alt.domain.error;

/**
 * 도메인별 ErrorCode enum이 구현하는 공통 인터페이스.
 * HTTP 상태 코드는 포함하지 않는다 — API 레이어가 ErrorCategory를 보고 결정한다 (DOM-ERR-001).
 */
public interface ErrorCode {

    String code();           // "{DOMAIN}-{NUMBER}" 형식 (예: SUB-001)

    String message();        // 사용자에게 노출 가능한 한국어 메시지

    ErrorCategory category();
}
