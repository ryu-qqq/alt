package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;

import java.util.Optional;

public interface ChannelQueryPort {

    Optional<Channel> findById(ChannelId id);

    boolean existsById(ChannelId id);
}
