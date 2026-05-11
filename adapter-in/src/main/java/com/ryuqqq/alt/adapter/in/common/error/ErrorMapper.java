package com.ryuqqq.alt.adapter.in.common.error;

import com.ryuqqq.alt.domain.error.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 도메인 예외 → HTTP 매핑 책임.
 * BC 별 매퍼를 구현하면 ErrorMapperRegistry 가 자동 라우팅한다.
 */
public interface ErrorMapper {

    boolean supports(DomainException exception);

    MappedError map(DomainException exception);

    /**
     * GlobalExceptionHandler 가 ProblemDetail 로 변환할 때 쓰는 매핑 결과.
     */
    record MappedError(
        HttpStatus status,
        String code,
        String title,
        String detail
    ) {
    }
}
