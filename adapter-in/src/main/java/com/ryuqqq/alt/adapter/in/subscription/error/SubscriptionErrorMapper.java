package com.ryuqqq.alt.adapter.in.subscription.error;

import com.ryuqqq.alt.adapter.in.common.error.ErrorMapper;
import com.ryuqqq.alt.domain.error.DomainException;
import com.ryuqqq.alt.domain.error.ErrorCategory;
import com.ryuqqq.alt.domain.error.ErrorCode;
import com.ryuqqq.alt.domain.error.SubscriptionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 구독 BC 도메인 예외 → ErrorCategory 기반 HTTP status 매핑.
 */
@Component
public class SubscriptionErrorMapper implements ErrorMapper {

    private static final Map<ErrorCategory, HttpStatus> CATEGORY_TO_STATUS = Map.of(
        ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND,
        ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST,
        ErrorCategory.CONFLICT, HttpStatus.CONFLICT,
        ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN
    );

    @Override
    public boolean supports(DomainException exception) {
        return exception instanceof SubscriptionException;
    }

    @Override
    public MappedError map(DomainException exception) {
        ErrorCode code = exception.errorCode();
        HttpStatus status = CATEGORY_TO_STATUS.getOrDefault(code.category(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new MappedError(status, code.code(), code.message(), exception.getMessage());
    }
}
