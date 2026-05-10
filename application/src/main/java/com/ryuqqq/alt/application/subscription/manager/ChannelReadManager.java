package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.ChannelQueryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ChannelReadManager {

    private final ChannelQueryPort channelQueryPort;

    public ChannelReadManager(ChannelQueryPort channelQueryPort) {
        this.channelQueryPort = channelQueryPort;
    }

    @Transactional(readOnly = true)
    public Channel getById(ChannelId id) {
        return channelQueryPort.findById(id)
            .orElseThrow(() -> new ChannelNotFoundException("channelId=" + id.value()));
    }

    @Transactional(readOnly = true)
    public void verifyExists(ChannelId id) {
        if (!channelQueryPort.existsById(id)) {
            throw new ChannelNotFoundException("channelId=" + id.value());
        }
    }
}
