package com.ryuqqq.alt.domain.channel;

import java.util.Objects;


/**
 * Channel Aggregate Root.
 *
 * - 정적 팩토리 forNew / reconstitute (DOM-AGG-001)
 * - LoD 준수: canSubscribe / canUnsubscribe / idValue 직접 노출
 * - equals/hashCode는 ID 기반 (DOM-AGG-010)
 */
public final class Channel {

    private final ChannelId id;
    private final String name;
    private final ChannelType type;

    private Channel(ChannelId id, String name, ChannelType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public static Channel forNew(String name, ChannelType type) {
        return new Channel(ChannelId.forNew(), name, type);
    }

    public static Channel reconstitute(ChannelId id, String name, ChannelType type) {
        return new Channel(id, name, type);
    }

    public boolean canSubscribe() {
        return type.canSubscribe();
    }

    public boolean canUnsubscribe() {
        return type.canUnsubscribe();
    }

    public ChannelId id() {
        return id;
    }

    public Long idValue() {
        return id.value();
    }

    public String name() {
        return name;
    }

    public ChannelType type() {
        return type;
    }

    public String typeDisplayName() {
        return type.displayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Channel other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
