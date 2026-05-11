package com.ryuqqq.alt.domain.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionErrorCode enum 의 ErrorCode 계약 검증.
 *
 * - code() / message() / category() 가 모두 non-null
 * - code 형식이 "{DOMAIN}-{NUMBER}" (예: SUB-001, MEM-001, CHN-001)
 * - 도메인별 ErrorCode 가 의미 있는 카테고리에 매핑
 */
@DisplayName("SubscriptionErrorCode enum")
class SubscriptionErrorCodeTest {

    @Nested
    @DisplayName("T-1. ErrorCode 계약")
    class ErrorCodeContract {

        @ParameterizedTest
        @EnumSource(SubscriptionErrorCode.class)
        @DisplayName("모든 ErrorCode 는 code/message/category 가 non-null")
        void allFieldsNotNull(SubscriptionErrorCode errorCode) {
            assertThat(errorCode.code()).isNotBlank();
            assertThat(errorCode.message()).isNotBlank();
            assertThat(errorCode.category()).isNotNull();
        }

        @ParameterizedTest
        @EnumSource(SubscriptionErrorCode.class)
        @DisplayName("code 는 '{DOMAIN}-{3자리}' 형식")
        void codeFormat(SubscriptionErrorCode errorCode) {
            assertThat(errorCode.code()).matches("^(MEM|CHN|SUB)-\\d{3}$");
        }
    }

    @Nested
    @DisplayName("T-1. 코드별 카테고리 매핑 검증")
    class CategoryMapping {

        @Test
        @DisplayName("Member / Channel NOT_FOUND 코드는 NOT_FOUND 카테고리")
        void notFoundCategory() {
            assertThat(SubscriptionErrorCode.MEMBER_NOT_FOUND.category()).isEqualTo(ErrorCategory.NOT_FOUND);
            assertThat(SubscriptionErrorCode.CHANNEL_NOT_FOUND.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        }

        @Test
        @DisplayName("입력값 위반은 VALIDATION 카테고리")
        void validationCategory() {
            assertThat(SubscriptionErrorCode.INVALID_PHONE_NUMBER.category()).isEqualTo(ErrorCategory.VALIDATION);
        }

        @Test
        @DisplayName("상태 전이/권한 위반은 FORBIDDEN 카테고리")
        void forbiddenCategory() {
            assertThat(SubscriptionErrorCode.CHANNEL_SUBSCRIBE_NOT_ALLOWED.category()).isEqualTo(ErrorCategory.FORBIDDEN);
            assertThat(SubscriptionErrorCode.CHANNEL_UNSUBSCRIBE_NOT_ALLOWED.category()).isEqualTo(ErrorCategory.FORBIDDEN);
            assertThat(SubscriptionErrorCode.INVALID_SUBSCRIBE_TRANSITION.category()).isEqualTo(ErrorCategory.FORBIDDEN);
            assertThat(SubscriptionErrorCode.INVALID_UNSUBSCRIBE_TRANSITION.category()).isEqualTo(ErrorCategory.FORBIDDEN);
        }

        @Test
        @DisplayName("멱등성 충돌은 CONFLICT 카테고리")
        void conflictCategory() {
            assertThat(SubscriptionErrorCode.IDEMPOTENCY_CONFLICT.category()).isEqualTo(ErrorCategory.CONFLICT);
        }
    }

    @Nested
    @DisplayName("T-1. ErrorCode 인터페이스 구현 — 다형성 사용 가능")
    class ErrorCodeInterface {

        @Test
        @DisplayName("SubscriptionErrorCode 는 ErrorCode 인터페이스로 다룰 수 있다")
        void usableAsErrorCode() {
            ErrorCode errorCode = SubscriptionErrorCode.IDEMPOTENCY_CONFLICT;

            assertThat(errorCode.code()).isEqualTo("SUB-201");
            assertThat(errorCode.message()).isEqualTo("동일 멱등성 키로 이미 처리된 요청입니다");
            assertThat(errorCode.category()).isEqualTo(ErrorCategory.CONFLICT);
        }
    }
}
