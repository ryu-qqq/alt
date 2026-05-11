package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.ChannelQueryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

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

    /**
     * 일괄 조회 결과를 Channels 컬렉션 VO 로 반환.
     * 빈 입력은 Channels.empty() 를 반환하며, 누락된 id 가 있어도 예외를 던지지 않는다 (호출자 graceful 처리).
     */
    @Transactional(readOnly = true)
    public Channels findByIds(Collection<ChannelId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Channels.empty();
        }
        return Channels.from(channelQueryPort.findByIds(ids));
    }
}
