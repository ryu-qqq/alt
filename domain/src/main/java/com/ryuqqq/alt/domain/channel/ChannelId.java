package com.ryuqqq.alt.domain.channel;

/**
 * Channel Aggregate Root의 ID VO (DOM-ID-001).
 */
public record ChannelId(Long value) {

    public ChannelId {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("ChannelId must be positive when present: " + value);
        }
    }

    public static ChannelId of(Long value) {
        return new ChannelId(value);
    }

    public static ChannelId forNew() {
        return new ChannelId(null);
    }

    public boolean isNew() {
        return value == null;
    }
}
