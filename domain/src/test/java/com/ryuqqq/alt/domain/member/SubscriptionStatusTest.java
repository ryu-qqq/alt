package com.ryuqqq.alt.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionStatus 전이 규칙")
class SubscriptionStatusTest {

    @Nested
    @DisplayName("T-4. 구독 가능 전이")
    class CanSubscribeTo {

        @ParameterizedTest(name = "{0} -> {1} 은(는) {2}")
        @CsvSource({
            "NONE,    BASIC,   true",
            "NONE,    PREMIUM, true",
            "BASIC,   PREMIUM, true",
            "BASIC,   BASIC,   false",
            "PREMIUM, BASIC,   false",
            "PREMIUM, PREMIUM, false",
            "PREMIUM, NONE,    false",
            "NONE,    NONE,    false"
        })
        void canSubscribeTo(SubscriptionStatus from, SubscriptionStatus to, boolean expected) {
            assertThat(from.canSubscribeTo(to)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("T-4. 해지 가능 전이")
    class CanUnsubscribeTo {

        @ParameterizedTest(name = "{0} -> {1} 은(는) {2}")
        @CsvSource({
            "PREMIUM, BASIC, true",
            "PREMIUM, NONE,  true",
            "BASIC,   NONE,  true",
            "BASIC,   BASIC, false",
            "NONE,    NONE,  false",
            "NONE,    BASIC, false",
            "PREMIUM, PREMIUM, false"
        })
        void canUnsubscribeTo(SubscriptionStatus from, SubscriptionStatus to, boolean expected) {
            assertThat(from.canUnsubscribeTo(to)).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("displayName은 한국어 표기")
    void displayName() {
        assertThat(SubscriptionStatus.NONE.displayName()).isEqualTo("구독 안함");
        assertThat(SubscriptionStatus.BASIC.displayName()).isEqualTo("일반 구독");
        assertThat(SubscriptionStatus.PREMIUM.displayName()).isEqualTo("프리미엄 구독");
    }
}
