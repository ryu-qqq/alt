package com.ryuqqq.alt.domain.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독 BC 예외 클래스들이 ErrorCode 매핑, 메시지 포맷, 카테고리 일관성을 만족하는지 검증.
 *
 * - DomainException(ErrorCode) : 메시지 = errorCode.message()
 * - DomainException(ErrorCode, detail) : 메시지 = "{errorCode.message()} : {detail}"
 * - 모든 예외는 SubscriptionException -> DomainException -> RuntimeException 상속
 * - errorCode().category() 가 SubscriptionErrorCode 의 정의와 일치
 */
@DisplayName("SubscriptionException 계열 예외")
class SubscriptionExceptionsTest {

    @Nested
    @DisplayName("T-1. no-arg 생성자 — ErrorCode.message() 만 노출")
    class NoArgConstructor {

        @Test
        @DisplayName("ChannelNotFoundException: CHN-001 / NOT_FOUND")
        void channelNotFound() {
            ChannelNotFoundException ex = new ChannelNotFoundException();

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.CHANNEL_NOT_FOUND);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.NOT_FOUND);
            assertThat(ex.errorCode().code()).isEqualTo("CHN-001");
            assertThat(ex.getMessage()).isEqualTo("채널을 찾을 수 없습니다");
            assertThat(ex.getMessage()).doesNotContain(":");
        }

        @Test
        @DisplayName("MemberNotFoundException: MEM-001 / NOT_FOUND")
        void memberNotFound() {
            MemberNotFoundException ex = new MemberNotFoundException();

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.MEMBER_NOT_FOUND);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.NOT_FOUND);
            assertThat(ex.errorCode().code()).isEqualTo("MEM-001");
            assertThat(ex.getMessage()).isEqualTo("회원을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("IdempotencyConflictException: SUB-201 / CONFLICT")
        void idempotencyConflict() {
            IdempotencyConflictException ex = new IdempotencyConflictException();

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.CONFLICT);
            assertThat(ex.errorCode().code()).isEqualTo("SUB-201");
            assertThat(ex.getMessage()).isEqualTo("동일 멱등성 키로 이미 처리된 요청입니다");
        }
    }

    @Nested
    @DisplayName("T-1. detail 생성자 — '{message} : {detail}' 포맷")
    class DetailConstructor {

        @Test
        @DisplayName("ChannelNotFoundException(detail): 메시지에 detail 포함")
        void channelNotFoundWithDetail() {
            ChannelNotFoundException ex = new ChannelNotFoundException("channelId=42");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.CHANNEL_NOT_FOUND);
            assertThat(ex.getMessage()).isEqualTo("채널을 찾을 수 없습니다 : channelId=42");
        }

        @Test
        @DisplayName("MemberNotFoundException(detail): 메시지에 detail 포함")
        void memberNotFoundWithDetail() {
            MemberNotFoundException ex = new MemberNotFoundException("memberId=99");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.MEMBER_NOT_FOUND);
            assertThat(ex.getMessage()).isEqualTo("회원을 찾을 수 없습니다 : memberId=99");
        }

        @Test
        @DisplayName("IdempotencyConflictException(idempotencyKey): 메시지에 'key=...' 형식")
        void idempotencyConflictWithKey() {
            IdempotencyConflictException ex = new IdempotencyConflictException("abc-123");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT);
            assertThat(ex.getMessage()).isEqualTo("동일 멱등성 키로 이미 처리된 요청입니다 : key=abc-123");
        }

        @Test
        @DisplayName("InvalidPhoneNumberException(detail) — 기존에 검증되지 않은 detail 포맷 확인")
        void invalidPhoneNumberWithDetail() {
            InvalidPhoneNumberException ex = new InvalidPhoneNumberException("010");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.INVALID_PHONE_NUMBER);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.VALIDATION);
            assertThat(ex.getMessage()).isEqualTo("유효하지 않은 휴대폰 번호입니다 : 010");
        }

        @Test
        @DisplayName("ChannelSubscribeNotAllowedException(detail) — FORBIDDEN 카테고리")
        void channelSubscribeNotAllowed() {
            ChannelSubscribeNotAllowedException ex = new ChannelSubscribeNotAllowedException("UNSUBSCRIBE_ONLY");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.CHANNEL_SUBSCRIBE_NOT_ALLOWED);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.FORBIDDEN);
            assertThat(ex.getMessage()).isEqualTo("해당 채널에서는 구독할 수 없습니다 : UNSUBSCRIBE_ONLY");
        }

        @Test
        @DisplayName("ChannelUnsubscribeNotAllowedException(detail) — FORBIDDEN 카테고리")
        void channelUnsubscribeNotAllowed() {
            ChannelUnsubscribeNotAllowedException ex = new ChannelUnsubscribeNotAllowedException("SUBSCRIBE_ONLY");

            assertThat(ex.errorCode()).isEqualTo(SubscriptionErrorCode.CHANNEL_UNSUBSCRIBE_NOT_ALLOWED);
            assertThat(ex.errorCode().category()).isEqualTo(ErrorCategory.FORBIDDEN);
            assertThat(ex.getMessage()).isEqualTo("해당 채널에서는 해지할 수 없습니다 : SUBSCRIBE_ONLY");
        }
    }

    @Nested
    @DisplayName("T-3. 상속 구조 — SubscriptionException -> DomainException -> RuntimeException")
    class InheritanceHierarchy {

        @ParameterizedTest(name = "{0}")
        @MethodSource("allConcreteExceptions")
        @DisplayName("모든 구체 예외는 SubscriptionException, DomainException, RuntimeException 의 자손")
        void inheritsExpectedHierarchy(String displayName, Supplier<DomainException> supplier) {
            DomainException ex = supplier.get();

            assertThat(ex)
                .isInstanceOf(SubscriptionException.class)
                .isInstanceOf(DomainException.class)
                .isInstanceOf(RuntimeException.class);
            assertThat(ex.errorCode()).isNotNull();
        }

        static Stream<org.junit.jupiter.params.provider.Arguments> allConcreteExceptions() {
            return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("ChannelNotFoundException()",
                    (Supplier<DomainException>) ChannelNotFoundException::new),
                org.junit.jupiter.params.provider.Arguments.of("MemberNotFoundException()",
                    (Supplier<DomainException>) MemberNotFoundException::new),
                org.junit.jupiter.params.provider.Arguments.of("IdempotencyConflictException()",
                    (Supplier<DomainException>) IdempotencyConflictException::new),
                org.junit.jupiter.params.provider.Arguments.of("ChannelNotFoundException(detail)",
                    (Supplier<DomainException>) () -> new ChannelNotFoundException("d")),
                org.junit.jupiter.params.provider.Arguments.of("MemberNotFoundException(detail)",
                    (Supplier<DomainException>) () -> new MemberNotFoundException("d")),
                org.junit.jupiter.params.provider.Arguments.of("IdempotencyConflictException(key)",
                    (Supplier<DomainException>) () -> new IdempotencyConflictException("k")),
                org.junit.jupiter.params.provider.Arguments.of("InvalidPhoneNumberException(detail)",
                    (Supplier<DomainException>) () -> new InvalidPhoneNumberException("d")),
                org.junit.jupiter.params.provider.Arguments.of("InvalidSubscribeTransitionException(detail)",
                    (Supplier<DomainException>) () -> new InvalidSubscribeTransitionException("d")),
                org.junit.jupiter.params.provider.Arguments.of("InvalidUnsubscribeTransitionException(detail)",
                    (Supplier<DomainException>) () -> new InvalidUnsubscribeTransitionException("d")),
                org.junit.jupiter.params.provider.Arguments.of("ChannelSubscribeNotAllowedException(detail)",
                    (Supplier<DomainException>) () -> new ChannelSubscribeNotAllowedException("d")),
                org.junit.jupiter.params.provider.Arguments.of("ChannelUnsubscribeNotAllowedException(detail)",
                    (Supplier<DomainException>) () -> new ChannelUnsubscribeNotAllowedException("d"))
            );
        }
    }
}
