package com.ryuqqq.alt.domain.channel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채널 일급 컬렉션 VO.
 *
 * 호출자가 {@code Map<ChannelId, Channel>} 같은 어댑터 자료구조를 직접 다루지 않고
 * 도메인 어휘(nameOf, findById) 로 채널을 조회하도록 캡슐화한다.
 *
 * 내부적으로 byId 맵을 미리 만들어 nameOf / findById 를 O(1) 로 보장한다.
 * 빌드 시점 외 변경 없음 — 입력 List 는 방어 복사.
 */
public final class Channels {

    private final List<Channel> items;
    private final Map<ChannelId, Channel> byId;

    private Channels(List<Channel> items, Map<ChannelId, Channel> byId) {
        this.items = items;
        this.byId = byId;
    }

    public static Channels from(List<Channel> items) {
        if (items == null || items.isEmpty()) {
            return empty();
        }
        Map<ChannelId, Channel> byId = items.stream()
            .collect(Collectors.toUnmodifiableMap(Channel::id, Function.identity()));
        return new Channels(List.copyOf(items), byId);
    }

    public static Channels empty() {
        return new Channels(List.of(), Map.of());
    }

    public List<Channel> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    public Optional<Channel> findById(ChannelId id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * 채널 ID 에 해당하는 채널명. 일치 채널이 없으면 빈 문자열.
     * 표현 단계 헬퍼 — 호출자가 Optional 분기를 매번 작성하지 않게 한다.
     */
    public String nameOf(ChannelId id) {
        Channel channel = byId.get(id);
        return channel != null ? channel.name() : "";
    }
}
