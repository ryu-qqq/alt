package com.ryuqqq.alt.domain.channel;

public final class Channel {

    private final ChannelId id;
    private final String name;
    private final ChannelType type;

    private Channel(ChannelId id, String name, ChannelType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public static Channel rehydrate(ChannelId id, String name, ChannelType type) {
        return new Channel(id, name, type);
    }

    public ChannelId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ChannelType type() {
        return type;
    }
}
