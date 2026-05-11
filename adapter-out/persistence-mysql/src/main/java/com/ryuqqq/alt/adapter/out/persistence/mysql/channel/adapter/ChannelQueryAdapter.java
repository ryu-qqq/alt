package com.ryuqqq.alt.adapter.out.persistence.mysql.channel.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.mapper.ChannelEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.repository.ChannelQueryDslRepository;
import com.ryuqqq.alt.application.subscription.port.out.ChannelQueryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class ChannelQueryAdapter implements ChannelQueryPort {

    private final ChannelQueryDslRepository channelQueryDslRepository;
    private final ChannelEntityMapper channelEntityMapper;

    public ChannelQueryAdapter(ChannelQueryDslRepository channelQueryDslRepository, ChannelEntityMapper channelEntityMapper) {
        this.channelQueryDslRepository = channelQueryDslRepository;
        this.channelEntityMapper = channelEntityMapper;
    }

    @Override
    public Optional<Channel> findById(ChannelId id) {
        return channelQueryDslRepository.findById(id.value())
            .map(channelEntityMapper::toDomain);
    }

    @Override
    public boolean existsById(ChannelId id) {
        return channelQueryDslRepository.existsById(id.value());
    }

    @Override
    public List<Channel> findByIds(Collection<ChannelId> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> rawIds = ids.stream().map(ChannelId::value).toList();
        return channelQueryDslRepository.findByIdIn(rawIds).stream()
            .map(channelEntityMapper::toDomain)
            .toList();
    }
}
