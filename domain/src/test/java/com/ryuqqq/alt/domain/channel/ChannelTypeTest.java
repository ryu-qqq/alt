package com.ryuqqq.alt.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelType 권한")
class ChannelTypeTest {

    @Test
    @DisplayName("T-4. SUBSCRIBE_ONLY는 구독만 가능")
    void subscribeOnly() {
        ChannelType type = ChannelType.SUBSCRIBE_ONLY;
        assertThat(type.canSubscribe()).isTrue();
        assertThat(type.canUnsubscribe()).isFalse();
    }

    @Test
    @DisplayName("T-4. UNSUBSCRIBE_ONLY는 해지만 가능")
    void unsubscribeOnly() {
        ChannelType type = ChannelType.UNSUBSCRIBE_ONLY;
        assertThat(type.canSubscribe()).isFalse();
        assertThat(type.canUnsubscribe()).isTrue();
    }

    @Test
    @DisplayName("T-4. BOTH는 구독/해지 모두 가능")
    void both() {
        ChannelType type = ChannelType.BOTH;
        assertThat(type.canSubscribe()).isTrue();
        assertThat(type.canUnsubscribe()).isTrue();
    }
}
