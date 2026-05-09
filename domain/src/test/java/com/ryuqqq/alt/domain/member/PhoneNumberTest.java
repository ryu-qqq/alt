package com.ryuqqq.alt.domain.member;

import com.ryuqqq.alt.domain.error.InvalidPhoneNumberException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PhoneNumber VO")
class PhoneNumberTest {

    @Nested
    @DisplayName("T-5. 유효한 입력")
    class Valid {

        @ParameterizedTest
        @ValueSource(strings = {
            "01012345678",
            "010-1234-5678",
            "010 1234 5678",
            "0101234567",     // 10자리 (예: 011 시절 잔존 또는 010 일부 패턴)
            "01112345678"
        })
        @DisplayName("정규화된 형태로 보관")
        void normalizes(String raw) {
            PhoneNumber phoneNumber = PhoneNumber.of(raw);
            assertThat(phoneNumber.value()).doesNotContain("-", " ");
        }
    }

    @Nested
    @DisplayName("T-5. 유효하지 않은 입력")
    class Invalid {

        @Test
        @DisplayName("null 입력 거부")
        void rejectsNull() {
            assertThatThrownBy(() -> PhoneNumber.of(null))
                .isInstanceOf(InvalidPhoneNumberException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "",
            "abc",
            "02-1234-5678",       // 지역번호
            "1234567890123",      // 너무 김
            "10012345678",        // 01x 패턴 위반
            "010"                 // 너무 짧음
        })
        @DisplayName("형식 위반 거부")
        void rejectsInvalidFormat(String raw) {
            assertThatThrownBy(() -> PhoneNumber.of(raw))
                .isInstanceOf(InvalidPhoneNumberException.class);
        }
    }
}
