package com.ryuqqq.alt.adapter.in.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ryuqqq.alt.adapter.in.subscription.error.SubscriptionErrorMapper;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.DomainException;
import com.ryuqqq.alt.domain.error.ErrorCategory;
import com.ryuqqq.alt.domain.error.ErrorCode;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler 통합 테스트.
 *
 * 테스트 전용 컨트롤러로 다양한 예외를 직접 발생시키고, ProblemDetail 본문/헤더가
 * RFC 7807 규격대로 매핑되는지 확인한다.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestExceptionController.class)
@Import({
    GlobalExceptionHandler.class,
    ErrorMapperRegistry.class,
    SubscriptionErrorMapper.class,
    GlobalExceptionHandlerTest.ServerErrorTestMapper.class
})
@DisplayName("GlobalExceptionHandler 검증")
class GlobalExceptionHandlerTest {

    private static final String BASE = "/__test__/errors";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("도메인 예외 매핑")
    class DomainExceptionMapping {

        @Test
        @DisplayName("MemberNotFoundException → 404 + MEM-001 + Problem JSON 포맷")
        void shouldReturn404WhenMemberNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/member-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("x-error-code", "MEM-001"))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("MEM-001"))
                .andExpect(jsonPath("$.title").value("회원을 찾을 수 없습니다"))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.instance").value("/__test__/errors/member-not-found"));
        }

        @Test
        @DisplayName("ChannelNotFoundException → 404 + CHN-001")
        void shouldReturn404WhenChannelNotFound() throws Exception {
            mockMvc.perform(get(BASE + "/channel-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("x-error-code", "CHN-001"))
                .andExpect(jsonPath("$.code").value("CHN-001"));
        }

        @Test
        @DisplayName("InvalidSubscribeTransitionException → 403 + SUB-001")
        void shouldReturn403WhenInvalidTransition() throws Exception {
            mockMvc.perform(get(BASE + "/invalid-transition"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("x-error-code", "SUB-001"))
                .andExpect(jsonPath("$.code").value("SUB-001"));
        }

        @Test
        @DisplayName("ChannelSubscribeNotAllowedException → 403 + CHN-002")
        void shouldReturn403WhenChannelNotAllowed() throws Exception {
            mockMvc.perform(get(BASE + "/channel-not-allowed"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("x-error-code", "CHN-002"))
                .andExpect(jsonPath("$.code").value("CHN-002"));
        }

        @Test
        @DisplayName("IdempotencyConflictException → 409 + SUB-201")
        void shouldReturn409WhenIdempotencyConflict() throws Exception {
            mockMvc.perform(get(BASE + "/idempotency-conflict"))
                .andExpect(status().isConflict())
                .andExpect(header().string("x-error-code", "SUB-201"))
                .andExpect(jsonPath("$.code").value("SUB-201"));
        }

        @Test
        @DisplayName("Subscription 매퍼가 처리하지 못하는 DomainException 은 default 매핑으로 ErrorCategory 기반 status 반환")
        void shouldFallbackToDefaultMappingForUnsupportedDomainException() throws Exception {
            mockMvc.perform(get(BASE + "/unsupported-domain"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "OTH-001"))
                .andExpect(jsonPath("$.code").value("OTH-001"));
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        @DisplayName("@RequestBody Validation 실패 → 400 + VALIDATION_FAILED + errors 필드")
        void shouldReturn400OnValidationFailure() throws Exception {
            mockMvc.perform(post(BASE + "/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "VALIDATION_FAILED"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists());
        }
    }

    @Nested
    @DisplayName("요청 파싱 실패")
    class RequestParsing {

        @Test
        @DisplayName("잘못된 JSON 본문 → 400 + MALFORMED_REQUEST")
        void shouldReturn400OnMalformedJson() throws Exception {
            mockMvc.perform(post(BASE + "/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }

        @Test
        @DisplayName("@RequestParam 타입 불일치 → 400 + TYPE_MISMATCH")
        void shouldReturn400OnTypeMismatch() throws Exception {
            mockMvc.perform(get(BASE + "/type-mismatch").param("value", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "TYPE_MISMATCH"))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"));
        }

        @Test
        @DisplayName("필수 헤더 누락 → 400 + MISSING_HEADER")
        void shouldReturn400OnMissingHeader() throws Exception {
            mockMvc.perform(get(BASE + "/require-header"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("x-error-code", "MISSING_HEADER"))
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
        }
    }

    @Nested
    @DisplayName("HTTP 메서드 미지원")
    class MethodNotSupported {

        @Test
        @DisplayName("정의되지 않은 메서드 호출 → 405 + METHOD_NOT_ALLOWED")
        void shouldReturn405WhenMethodNotSupported() throws Exception {
            mockMvc.perform(post(BASE + "/member-not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("x-error-code", "METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        }
    }

    @Nested
    @DisplayName("예상치 못한 예외")
    class UnexpectedException {

        @Test
        @DisplayName("Exception fallback → 500 + INTERNAL_ERROR")
        void shouldReturn500WhenUnexpectedException() throws Exception {
            mockMvc.perform(get(BASE + "/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("x-error-code", "INTERNAL_ERROR"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"));
        }

        @Test
        @DisplayName("매핑이 안 되는 도메인 예외 (카테고리 매핑 외) → 500 으로 logByStatus 5xx 분기 검증")
        void shouldReturn500WhenDomainExceptionMappedTo5xx() throws Exception {
            mockMvc.perform(get(BASE + "/unsupported-domain-server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("x-error-code", "OTH-500"))
                .andExpect(jsonPath("$.code").value("OTH-500"));
        }
    }

    @Nested
    @DisplayName("MDC traceId")
    class MdcTraceId {

        @Test
        @DisplayName("MDC 에 traceId 가 설정되어 있으면 응답 traceId 로 사용된다")
        void shouldUseMdcTraceIdIfPresent() throws Exception {
            try {
                org.slf4j.MDC.put("traceId", "mdc-trace-1234");
                mockMvc.perform(get(BASE + "/member-not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.traceId").value("mdc-trace-1234"));
            } finally {
                org.slf4j.MDC.remove("traceId");
            }
        }
    }

    /**
     * 테스트 전용 컨트롤러. GlobalExceptionHandler 를 자극할 다양한 예외를 던진다.
     */
    @RestController
    @RequestMapping("/__test__/errors")
    static class TestExceptionController {

        @GetMapping("/member-not-found")
        public String memberNotFound() {
            throw new MemberNotFoundException("01099999999");
        }

        @GetMapping("/channel-not-found")
        public String channelNotFound() {
            throw new ChannelNotFoundException("id=7");
        }

        @GetMapping("/invalid-transition")
        public String invalidTransition() {
            throw new InvalidSubscribeTransitionException("PREMIUM -> BASIC");
        }

        @GetMapping("/channel-not-allowed")
        public String channelNotAllowed() {
            throw new ChannelSubscribeNotAllowedException("UNSUBSCRIBE_ONLY");
        }

        @GetMapping("/idempotency-conflict")
        public String idempotencyConflict() {
            throw new IdempotencyConflictException("dup-key");
        }

        @GetMapping("/unsupported-domain")
        public String unsupportedDomain() {
            throw new OtherDomainException();
        }

        @PostMapping("/validate")
        public String validate(@jakarta.validation.Valid @RequestBody ValidatePayload payload) {
            return payload.name();
        }

        @GetMapping("/type-mismatch")
        public String typeMismatch(@RequestParam("value") Long value) {
            return String.valueOf(value);
        }

        @GetMapping("/require-header")
        public String requireHeader(@RequestHeader("X-Required") String header) {
            return header;
        }

        @GetMapping("/unexpected")
        public String unexpected() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/unsupported-domain-server-error")
        public String unsupportedDomainServerError() {
            throw new ServerErrorDomainException();
        }
    }

    /**
     * 도메인 예외가 5xx 로 매핑되는 케이스를 시뮬레이션하기 위한 테스트용 매퍼.
     * logByStatus 의 5xx 분기 커버리지를 위해 사용된다.
     */
    static class ServerErrorTestMapper implements com.ryuqqq.alt.adapter.in.common.error.ErrorMapper {
        @Override
        public boolean supports(DomainException exception) {
            return exception instanceof ServerErrorDomainException;
        }

        @Override
        public MappedError map(DomainException exception) {
            return new MappedError(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                exception.errorCode().code(),
                exception.errorCode().message(),
                exception.getMessage()
            );
        }
    }

    /**
     * 5xx 매핑 케이스 테스트용 도메인 예외.
     */
    static final class ServerErrorDomainException extends DomainException {
        ServerErrorDomainException() {
            super(new ErrorCode() {
                @Override public String code() { return "OTH-500"; }
                @Override public String message() { return "server error in domain"; }
                @Override public ErrorCategory category() { return ErrorCategory.VALIDATION; }
            });
        }
    }

    /**
     * Validation 실패를 유발하기 위한 페이로드.
     */
    record ValidatePayload(
        @jakarta.validation.constraints.NotBlank(message = "이름은 필수입니다")
        String name
    ) {
    }

    /**
     * SubscriptionErrorMapper.supports == false 인 도메인 예외.
     * → ErrorMapperRegistry 의 defaultMapping 으로 fallback.
     * ErrorCategory.VALIDATION 이므로 400 + 자체 code 가 노출되어야 한다.
     */
    static final class OtherDomainException extends DomainException {
        OtherDomainException() {
            super(new ErrorCode() {
                @Override public String code() { return "OTH-001"; }
                @Override public String message() { return "other error"; }
                @Override public ErrorCategory category() { return ErrorCategory.VALIDATION; }
            });
        }
    }
}
