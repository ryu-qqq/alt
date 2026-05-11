package com.ryuqqq.alt.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChannelId VO")
class ChannelIdTest {

    @Nested
    @DisplayName("T-1. 생성 — of")
    class Creation {

        @Test
        @DisplayName("양수 값으로 생성 가능")
        void positiveValue() {
            ChannelId id = ChannelId.of(10L);

            assertThat(id.value()).isEqualTo(10L);
            assertThat(id.isNew()).isFalse();
        }

        @Test
        @DisplayName("null 값은 신규(isNew=true)")
        void nullIsNew() {
            ChannelId id = ChannelId.of(null);

            assertThat(id.value()).isNull();
            assertThat(id.isNew()).isTrue();
        }

        @Test
        @DisplayName("0 또는 음수는 IllegalArgumentException")
        void rejectsNonPositive() {
            assertThatThrownBy(() -> ChannelId.of(0L))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ChannelId.of(-1L))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("T-2. forNew")
    class ForNew {

        @Test
        @DisplayName("forNew()는 isNew=true 인 ID 를 반환")
        void forNew() {
            ChannelId id = ChannelId.forNew();

            assertThat(id.isNew()).isTrue();
            assertThat(id.value()).isNull();
        }
    }

    @Nested
    @DisplayName("T-3. 동등성 (record 기본)")
    class Equality {

        @Test
        @DisplayName("같은 값이면 equals=true")
        void sameValueEquals() {
            assertThat(ChannelId.of(10L)).isEqualTo(ChannelId.of(10L));
        }

        @Test
        @DisplayName("다른 값이면 equals=false")
        void differentValueNotEquals() {
            assertThat(ChannelId.of(10L)).isNotEqualTo(ChannelId.of(11L));
        }

        @Test
        @DisplayName("forNew 끼리는 모두 null 값으로 같다")
        void forNewEquals() {
            assertThat(ChannelId.forNew()).isEqualTo(ChannelId.forNew());
        }
    }
}
