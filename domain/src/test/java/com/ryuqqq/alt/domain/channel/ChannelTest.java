package com.ryuqqq.alt.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Channel Aggregate")
class ChannelTest {

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("forNew는 ID가 isNew=true")
        void forNewChannel() {
            Channel channel = Channel.forNew("홈페이지", ChannelType.BOTH);
            assertThat(channel.id().isNew()).isTrue();
            assertThat(channel.name()).isEqualTo("홈페이지");
            assertThat(channel.type()).isEqualTo(ChannelType.BOTH);
        }

        @Test
        @DisplayName("reconstitute는 모든 필드를 보존")
        void reconstitute() {
            ChannelId id = ChannelId.of(10L);
            Channel channel = Channel.reconstitute(id, "콜센터", ChannelType.UNSUBSCRIBE_ONLY);
            assertThat(channel.id()).isEqualTo(id);
            assertThat(channel.idValue()).isEqualTo(10L);
            assertThat(channel.name()).isEqualTo("콜센터");
            assertThat(channel.type()).isEqualTo(ChannelType.UNSUBSCRIBE_ONLY);
        }
    }

    @Nested
    @DisplayName("T-4. 권한 위임 (LoD)")
    class PermissionDelegation {

        @Test
        @DisplayName("BOTH 채널은 구독/해지 모두 가능")
        void bothAllows() {
            Channel channel = Channel.forNew("모바일앱", ChannelType.BOTH);
            assertThat(channel.canSubscribe()).isTrue();
            assertThat(channel.canUnsubscribe()).isTrue();
        }

        @Test
        @DisplayName("SUBSCRIBE_ONLY 채널은 구독만 가능")
        void subscribeOnly() {
            Channel channel = Channel.forNew("네이버", ChannelType.SUBSCRIBE_ONLY);
            assertThat(channel.canSubscribe()).isTrue();
            assertThat(channel.canUnsubscribe()).isFalse();
        }

        @Test
        @DisplayName("typeDisplayName은 enum displayName 위임")
        void typeDisplayName() {
            Channel channel = Channel.forNew("이메일", ChannelType.UNSUBSCRIBE_ONLY);
            assertThat(channel.typeDisplayName()).isEqualTo("해지 전용");
        }
    }
}
