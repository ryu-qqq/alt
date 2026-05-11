package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelQueryPort {

    Optional<Channel> findById(ChannelId id);

    boolean existsById(ChannelId id);

    /**
     * 여러 채널을 일괄 조회. 순서·중복 정책은 어댑터가 결정한다.
     * 빈 입력은 빈 리스트를 반환한다 (호출자 가드 불필요).
     */
    List<Channel> findByIds(Collection<ChannelId> ids);
}
