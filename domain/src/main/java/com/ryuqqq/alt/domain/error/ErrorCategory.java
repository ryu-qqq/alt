package com.ryuqqq.alt.domain.error;

/**
 * 도메인 에러의 의미 분류. API 레이어가 HTTP 상태 코드 매핑 시 근거로 사용한다.
 * 도메인은 HTTP를 알지 않는다 (DOM-ERR-001).
 */
public enum ErrorCategory {

    NOT_FOUND,    // 리소스 없음
    VALIDATION,   // 입력값 / 비즈니스 규칙 위반
    CONFLICT,     // 상태 충돌 (중복, 동시성 등)
    FORBIDDEN     // 금지된 행위 (권한, 상태 전이 불가 등)
}
