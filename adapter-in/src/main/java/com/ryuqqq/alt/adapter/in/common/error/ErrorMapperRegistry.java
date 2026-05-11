package com.ryuqqq.alt.adapter.in.common.error;

import com.ryuqqq.alt.domain.error.DomainException;
import com.ryuqqq.alt.domain.error.ErrorCategory;
import com.ryuqqq.alt.domain.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 등록된 모든 ErrorMapper 중 supports 를 만족하는 첫 매퍼로 위임.
 * 매칭 실패 시 ErrorCategory 기반 defaultMapping 으로 fallback.
 */
@Component
public class ErrorMapperRegistry {

    private static final Map<ErrorCategory, HttpStatus> DEFAULT_CATEGORY_TO_STATUS = Map.of(
        ErrorCategory.NOT_FOUND, HttpStatus.NOT_FOUND,
        ErrorCategory.VALIDATION, HttpStatus.BAD_REQUEST,
        ErrorCategory.CONFLICT, HttpStatus.CONFLICT,
        ErrorCategory.FORBIDDEN, HttpStatus.FORBIDDEN
    );

    private final List<ErrorMapper> mappers;

    public ErrorMapperRegistry(List<ErrorMapper> mappers) {
        this.mappers = mappers;
    }

    public ErrorMapper.MappedError resolve(DomainException exception) {
        return mappers.stream()
            .filter(mapper -> mapper.supports(exception))
            .findFirst()
            .map(mapper -> mapper.map(exception))
            .orElseGet(() -> defaultMapping(exception));
    }

    private ErrorMapper.MappedError defaultMapping(DomainException exception) {
        ErrorCode code = exception.errorCode();
        HttpStatus status = DEFAULT_CATEGORY_TO_STATUS.getOrDefault(code.category(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new ErrorMapper.MappedError(status, code.code(), code.message(), exception.getMessage());
    }
}
