package com.ryuqqq.alt.adapter.in.common.error;

import com.ryuqqq.alt.domain.error.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RFC 7807 ProblemDetail 형식으로 에러 응답 통일.
 *
 * - 도메인 예외 → ErrorMapperRegistry → BC 매퍼 → HTTP status / code 결정
 * - 입력 검증 / 메시지 파싱 / HTTP 메서드 / 헤더 누락 / 타입 불일치 → 400 또는 405
 * - 그 외 → 500
 *
 * 모든 응답에 traceId / timestamp / x-error-code 헤더 부여.
 * 로그 레벨: 5xx → error, 4xx → warn (404 → debug).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String HEADER_ERROR_CODE = "x-error-code";
    private static final String PROPERTY_CODE = "code";
    private static final String PROPERTY_TIMESTAMP = "timestamp";
    private static final String PROPERTY_TRACE_ID = "traceId";
    private static final String PROPERTY_ERRORS = "errors";

    private final ErrorMapperRegistry errorMapperRegistry;

    public GlobalExceptionHandler(ErrorMapperRegistry errorMapperRegistry) {
        this.errorMapperRegistry = errorMapperRegistry;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException exception, HttpServletRequest request) {
        ErrorMapper.MappedError mapped = errorMapperRegistry.resolve(exception);
        logByStatus(mapped.status(), mapped.code(), exception);

        return buildProblemDetail(mapped.status(), mapped.code(), mapped.title(), mapped.detail(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효하지 않은 값",
                (existing, replacement) -> existing
            ));

        log.warn("validation failed: {}", fieldErrors);

        ResponseEntity<ProblemDetail> response = buildProblemDetail(
            HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Validation Failed",
            "입력값이 올바르지 않습니다", request
        );
        response.getBody().setProperty(PROPERTY_ERRORS, fieldErrors);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        log.warn("malformed request body: {}", exception.getMostSpecificCause().getMessage());
        return buildProblemDetail(
            HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed Request Body",
            "요청 본문 형식이 올바르지 않습니다", request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        log.warn("argument type mismatch: param={} value={}", exception.getName(), exception.getValue());
        return buildProblemDetail(
            HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "Argument Type Mismatch",
            "요청 파라미터 '" + exception.getName() + "' 의 타입이 올바르지 않습니다", request
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(MissingRequestHeaderException exception, HttpServletRequest request) {
        log.warn("missing required header: {}", exception.getHeaderName());
        return buildProblemDetail(
            HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing Request Header",
            "필수 헤더 '" + exception.getHeaderName() + "' 가 누락되었습니다", request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        log.warn("method not supported: {}", exception.getMethod());
        return buildProblemDetail(
            HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method Not Allowed",
            "HTTP 메서드 '" + exception.getMethod() + "' 가 지원되지 않습니다", request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("unexpected error", exception);
        return buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal Server Error",
            "예기치 않은 오류가 발생했습니다", request
        );
    }

    private ResponseEntity<ProblemDetail> buildProblemDetail(
        HttpStatus status, String code, String title, String detail, HttpServletRequest request
    ) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("about:blank"));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty(PROPERTY_CODE, code);
        body.setProperty(PROPERTY_TIMESTAMP, Instant.now().toString());
        body.setProperty(PROPERTY_TRACE_ID, resolveTraceId());

        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .header(HEADER_ERROR_CODE, code)
            .body(body);
    }

    private void logByStatus(HttpStatus status, String code, DomainException exception) {
        if (status.is5xxServerError()) {
            log.error("domain exception (5xx) code={} message={}", code, exception.getMessage(), exception);
        } else if (status == HttpStatus.NOT_FOUND) {
            log.debug("domain exception (404) code={} message={}", code, exception.getMessage());
        } else {
            log.warn("domain exception ({}) code={} message={}", status.value(), code, exception.getMessage());
        }
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
