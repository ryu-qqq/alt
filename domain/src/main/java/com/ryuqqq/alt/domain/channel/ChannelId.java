package com.ryuqqq.alt.domain.channel;

public record ChannelId(long value) {

    public ChannelId {
        if (value <= 0) {
            throw new IllegalArgumentException("ChannelId must be positive: " + value);
        }
    }
}
