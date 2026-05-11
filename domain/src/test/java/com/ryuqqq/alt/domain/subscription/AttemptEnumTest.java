package com.ryuqqq.alt.domain.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AttemptKind / AttemptStatus / AttemptFailureReason enum 의 displayName 캡슐화 검증.
 * 호출자가 enum.name() 대신 displayName() 을 통해 사용자 표현을 얻도록 강제하기 위한 회귀 테스트.
 */
@DisplayName("Subscription Enum displayName 회귀")
class AttemptEnumTest {

    @Nested
    @DisplayName("AttemptKind")
    class Kind {

        @Test
        @DisplayName("displayName 매핑")
        void displayNames() {
            assertThat(AttemptKind.SUBSCRIBE.displayName()).isEqualTo("구독");
            assertThat(AttemptKind.UNSUBSCRIBE.displayName()).isEqualTo("해지");
        }

        @ParameterizedTest
        @EnumSource(AttemptKind.class)
        @DisplayName("모든 값이 비어있지 않은 displayName 을 가진다")
        void allHaveDisplayName(AttemptKind kind) {
            assertThat(kind.displayName()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("AttemptStatus")
    class Status {

        @Test
        @DisplayName("displayName 매핑")
        void displayNames() {
            assertThat(AttemptStatus.COMMITTED.displayName()).isEqualTo("커밋");
            assertThat(AttemptStatus.ROLLED_BACK.displayName()).isEqualTo("롤백");
            assertThat(AttemptStatus.FAILED.displayName()).isEqualTo("실패");
        }

        @ParameterizedTest
        @EnumSource(AttemptStatus.class)
        @DisplayName("모든 값이 비어있지 않은 displayName 을 가진다")
        void allHaveDisplayName(AttemptStatus status) {
            assertThat(status.displayName()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("AttemptFailureReason")
    class FailureReason {

        @ParameterizedTest
        @EnumSource(AttemptFailureReason.class)
        @DisplayName("모든 값이 비어있지 않은 displayName 을 가진다")
        void allHaveDisplayName(AttemptFailureReason reason) {
            assertThat(reason.displayName()).isNotBlank();
        }

        @Test
        @DisplayName("EXTERNAL_REJECTED 는 ROLLED_BACK 사유로 사용된다 (회귀)")
        void rejectedDisplay() {
            assertThat(AttemptFailureReason.EXTERNAL_REJECTED.displayName())
                .isEqualTo("외부 응답으로 인한 의도된 롤백");
        }
    }
}
