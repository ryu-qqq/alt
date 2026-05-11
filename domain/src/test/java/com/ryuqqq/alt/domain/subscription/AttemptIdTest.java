package com.ryuqqq.alt.domain.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AttemptId VO")
class AttemptIdTest {

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("양수 값으로 생성 가능")
        void positive() {
            AttemptId id = AttemptId.of(99L);

            assertThat(id.value()).isEqualTo(99L);
            assertThat(id.isNew()).isFalse();
        }

        @Test
        @DisplayName("null은 신규")
        void nullIsNew() {
            AttemptId id = AttemptId.of(null);

            assertThat(id.isNew()).isTrue();
        }

        @Test
        @DisplayName("0 / 음수는 IllegalArgumentException")
        void rejectsNonPositive() {
            assertThatThrownBy(() -> AttemptId.of(0L))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> AttemptId.of(-100L))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("forNew(): isNew=true")
        void forNew() {
            assertThat(AttemptId.forNew().isNew()).isTrue();
        }
    }
}
