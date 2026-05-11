package com.ryuqqq.alt.adapter.in.subscription.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.alt.adapter.in.common.error.ErrorMapper;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import com.ryuqqq.alt.domain.error.ChannelSubscribeNotAllowedException;
import com.ryuqqq.alt.domain.error.DomainException;
import com.ryuqqq.alt.domain.error.ErrorCategory;
import com.ryuqqq.alt.domain.error.ErrorCode;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import com.ryuqqq.alt.domain.error.InvalidPhoneNumberException;
import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.error.SubscriptionErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

/**
 * SubscriptionErrorMapper 단위 테스트.
 * Spring 컨텍스트 없이 매퍼 자체의 supports / map 동작을 검증한다.
 */
@DisplayName("SubscriptionErrorMapper 검증")
class SubscriptionErrorMapperTest {

    private final SubscriptionErrorMapper mapper = new SubscriptionErrorMapper();

    @Nested
    @DisplayName("supports")
    class Supports {

        @Test
        @DisplayName("SubscriptionException 하위 예외는 true 를 반환한다")
        void shouldSupportSubscriptionException() {
            DomainException exception = new MemberNotFoundException();

            assertThat(mapper.supports(exception)).isTrue();
        }

        @Test
        @DisplayName("InvalidPhoneNumberException 도 SubscriptionException 이라 true 다")
        void shouldSupportInvalidPhoneNumberException() {
            DomainException exception = new InvalidPhoneNumberException("test");

            assertThat(mapper.supports(exception)).isTrue();
        }

        @Test
        @DisplayName("IdempotencyConflictException 도 true")
        void shouldSupportIdempotencyConflictException() {
            DomainException exception = new IdempotencyConflictException("key");

            assertThat(mapper.supports(exception)).isTrue();
        }

        @Test
        @DisplayName("SubscriptionException 이 아닌 DomainException 은 false")
        void shouldNotSupportNonSubscriptionException() {
            DomainException exception = new OtherDomainException();

            assertThat(mapper.supports(exception)).isFalse();
        }
    }

    @Nested
    @DisplayName("map - 카테고리별 HTTP status 매핑")
    class MapByCategory {

        @ParameterizedTest(name = "[{index}] {0} 예외 → status {1}")
        @MethodSource("categoryMappingCases")
        @DisplayName("ErrorCategory 별 HTTP status 매핑")
        void shouldMapCategoryToHttpStatus(
            DomainException exception,
            HttpStatus expectedStatus,
            String expectedCode
        ) {
            ErrorMapper.MappedError result = mapper.map(exception);

            assertThat(result.status()).isEqualTo(expectedStatus);
            assertThat(result.code()).isEqualTo(expectedCode);
        }

        static Stream<Arguments> categoryMappingCases() {
            return Stream.of(
                Arguments.of(
                    new MemberNotFoundException(),
                    HttpStatus.NOT_FOUND,
                    "MEM-001"
                ),
                Arguments.of(
                    new ChannelNotFoundException(),
                    HttpStatus.NOT_FOUND,
                    "CHN-001"
                ),
                Arguments.of(
                    new InvalidPhoneNumberException("bad phone"),
                    HttpStatus.BAD_REQUEST,
                    "MEM-002"
                ),
                Arguments.of(
                    new InvalidSubscribeTransitionException("NONE -> NONE"),
                    HttpStatus.FORBIDDEN,
                    "SUB-001"
                ),
                Arguments.of(
                    new ChannelSubscribeNotAllowedException("not allowed"),
                    HttpStatus.FORBIDDEN,
                    "CHN-002"
                ),
                Arguments.of(
                    new IdempotencyConflictException("key-1"),
                    HttpStatus.CONFLICT,
                    "SUB-201"
                )
            );
        }
    }

    @Nested
    @DisplayName("map - title/detail")
    class MapTitleAndDetail {

        @Test
        @DisplayName("title 은 ErrorCode 의 message 가 된다")
        void shouldUseErrorCodeMessageAsTitle() {
            DomainException exception = new MemberNotFoundException();

            ErrorMapper.MappedError result = mapper.map(exception);

            assertThat(result.title()).isEqualTo(SubscriptionErrorCode.MEMBER_NOT_FOUND.message());
        }

        @Test
        @DisplayName("detail 은 예외 메시지 전체 (message + detail) 가 된다")
        void shouldUseExceptionMessageAsDetail() {
            DomainException exception = new InvalidSubscribeTransitionException("BASIC -> NONE");

            ErrorMapper.MappedError result = mapper.map(exception);

            assertThat(result.detail()).contains("BASIC -> NONE");
        }
    }

    /**
     * SubscriptionException 이 아닌 도메인 예외 (테스트용).
     */
    private static final class OtherDomainException extends DomainException {
        OtherDomainException() {
            super(new ErrorCode() {
                @Override public String code() { return "OTH-001"; }
                @Override public String message() { return "other domain error"; }
                @Override public ErrorCategory category() { return ErrorCategory.VALIDATION; }
            });
        }
    }
}
