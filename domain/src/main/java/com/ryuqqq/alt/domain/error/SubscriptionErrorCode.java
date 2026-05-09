package com.ryuqqq.alt.domain.error;

/**
 * 구독 도메인(단일 BC)의 ErrorCode.
 * 코드 접두사 SUB-, MEM-, CHN- 으로 영역을 구분하되 enum 자체는 통합한다.
 */
public enum SubscriptionErrorCode implements ErrorCode {

    // Member
    MEMBER_NOT_FOUND("MEM-001", "회원을 찾을 수 없습니다", ErrorCategory.NOT_FOUND),
    INVALID_PHONE_NUMBER("MEM-002", "유효하지 않은 휴대폰 번호입니다", ErrorCategory.VALIDATION),

    // Channel
    CHANNEL_NOT_FOUND("CHN-001", "채널을 찾을 수 없습니다", ErrorCategory.NOT_FOUND),
    CHANNEL_SUBSCRIBE_NOT_ALLOWED("CHN-002", "해당 채널에서는 구독할 수 없습니다", ErrorCategory.FORBIDDEN),
    CHANNEL_UNSUBSCRIBE_NOT_ALLOWED("CHN-003", "해당 채널에서는 해지할 수 없습니다", ErrorCategory.FORBIDDEN),

    // Subscription transition
    INVALID_SUBSCRIBE_TRANSITION("SUB-001", "허용되지 않는 구독 상태 전이입니다", ErrorCategory.FORBIDDEN),
    INVALID_UNSUBSCRIBE_TRANSITION("SUB-002", "허용되지 않는 해지 상태 전이입니다", ErrorCategory.FORBIDDEN),

    // Attempt state machine
    ATTEMPT_NOT_PENDING("SUB-101", "이미 종료된 시도는 변경할 수 없습니다", ErrorCategory.CONFLICT);

    private final String code;
    private final String message;
    private final ErrorCategory category;

    SubscriptionErrorCode(String code, String message, ErrorCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
