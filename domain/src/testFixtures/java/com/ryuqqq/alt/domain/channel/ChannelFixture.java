package com.ryuqqq.alt.domain.channel;

import java.util.List;

/**
 * Channel BC 테스트용 Fixture.
 *
 * - 신규(forNew) / DB 복원(reconstitute) 양쪽을 모두 제공한다.
 * - 채널 일급 컬렉션(Channels) 픽스쳐도 함께 제공한다.
 */
public final class ChannelFixture {

    private ChannelFixture() {}

    public static final long DEFAULT_ID = 10L;
    public static final String DEFAULT_NAME = "기본 채널";

    public static final String SUBSCRIBE_ONLY_NAME = "구독 전용 채널";
    public static final String UNSUBSCRIBE_ONLY_NAME = "해지 전용 채널";
    public static final String BOTH_NAME = "구독/해지 채널";

    public static Channel newChannel() {
        return Channel.forNew(DEFAULT_NAME, ChannelType.BOTH);
    }

    public static Channel newChannel(ChannelType type) {
        return Channel.forNew(DEFAULT_NAME, type);
    }

    public static Channel reconstitutedChannel() {
        return Channel.reconstitute(ChannelId.of(DEFAULT_ID), DEFAULT_NAME, ChannelType.BOTH);
    }

    public static Channel reconstituted(long id, ChannelType type) {
        return Channel.reconstitute(ChannelId.of(id), DEFAULT_NAME, type);
    }

    public static Channel reconstituted(long id, String name, ChannelType type) {
        return Channel.reconstitute(ChannelId.of(id), name, type);
    }

    public static Channel subscribeOnlyChannel() {
        return Channel.reconstitute(ChannelId.of(11L), SUBSCRIBE_ONLY_NAME, ChannelType.SUBSCRIBE_ONLY);
    }

    public static Channel unsubscribeOnlyChannel() {
        return Channel.reconstitute(ChannelId.of(12L), UNSUBSCRIBE_ONLY_NAME, ChannelType.UNSUBSCRIBE_ONLY);
    }

    public static Channel bothChannel() {
        return Channel.reconstitute(ChannelId.of(13L), BOTH_NAME, ChannelType.BOTH);
    }

    public static Channels defaultChannels() {
        return Channels.from(List.of(subscribeOnlyChannel(), unsubscribeOnlyChannel(), bothChannel()));
    }

    public static Channels emptyChannels() {
        return Channels.empty();
    }
}
