package com.ryuqqq.alt.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Channels 일급 컬렉션 VO")
class ChannelsTest {

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("from(List): 입력 채널들을 보존")
        void fromList() {
            Channels channels = ChannelFixture.defaultChannels();

            assertThat(channels.size()).isEqualTo(3);
            assertThat(channels.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("from(null): empty() 와 동일")
        void fromNull() {
            Channels channels = Channels.from(null);

            assertThat(channels.isEmpty()).isTrue();
            assertThat(channels.size()).isZero();
        }

        @Test
        @DisplayName("from(emptyList): empty() 와 동일")
        void fromEmpty() {
            Channels channels = Channels.from(List.of());

            assertThat(channels.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("empty(): 빈 컬렉션")
        void emptyFactory() {
            Channels channels = ChannelFixture.emptyChannels();

            assertThat(channels.isEmpty()).isTrue();
            assertThat(channels.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("T-2. 조회")
    class Lookup {

        @Test
        @DisplayName("findById: 존재하면 Optional 에 채널 담아 반환")
        void findByIdHit() {
            Channels channels = ChannelFixture.defaultChannels();
            ChannelId id = ChannelId.of(11L);

            assertThat(channels.findById(id))
                .isPresent()
                .get()
                .extracting(Channel::name)
                .isEqualTo(ChannelFixture.SUBSCRIBE_ONLY_NAME);
        }

        @Test
        @DisplayName("findById: 없으면 Optional.empty()")
        void findByIdMiss() {
            Channels channels = ChannelFixture.defaultChannels();

            assertThat(channels.findById(ChannelId.of(999L))).isEmpty();
        }

        @Test
        @DisplayName("nameOf: 존재하면 채널명, 없으면 빈 문자열")
        void nameOf() {
            Channels channels = ChannelFixture.defaultChannels();

            assertThat(channels.nameOf(ChannelId.of(11L))).isEqualTo(ChannelFixture.SUBSCRIBE_ONLY_NAME);
            assertThat(channels.nameOf(ChannelId.of(999L))).isEmpty();
        }
    }

    @Nested
    @DisplayName("T-3. 불변식")
    class Invariants {

        @Test
        @DisplayName("입력 리스트를 외부에서 변경해도 VO 내부는 영향 없음 (defensive copy)")
        void defensivelyCopied() {
            List<Channel> mutable = new ArrayList<>();
            mutable.add(ChannelFixture.bothChannel());

            Channels channels = Channels.from(mutable);
            mutable.clear();

            assertThat(channels.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("items() 가 반환한 리스트는 수정 불가")
        void itemsImmutable() {
            Channels channels = ChannelFixture.defaultChannels();

            assertThatThrownBy(() -> channels.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
