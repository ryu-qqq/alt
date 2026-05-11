package com.ryuqqq.alt.adapter.out.persistence.mysql.channel.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.ChannelJpaEntity;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import org.springframework.stereotype.Component;

@Component
public class ChannelEntityMapper {

    public Channel toDomain(ChannelJpaEntity entity) {
        return Channel.reconstitute(
            ChannelId.of(entity.getId()),
            entity.getName(),
            entity.getType()
        );
    }
}
