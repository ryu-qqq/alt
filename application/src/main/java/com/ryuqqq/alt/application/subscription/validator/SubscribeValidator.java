package com.ryuqqq.alt.application.subscription.validator;

import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.domain.channel.Channel;
import org.springframework.stereotype.Component;

/**
 * 구독 UseCase 진입 시점 검증.
 * 채널 권한 + 상태 전이 가능성은 도메인 SubscriptionTransitionPolicy 가 담당하므로
 * 여기서는 채널 존재만 확인하고 채널을 반환한다.
 */
@Component
public class SubscribeValidator {

    private final ChannelReadManager channelReadManager;

    public SubscribeValidator(ChannelReadManager channelReadManager) {
        this.channelReadManager = channelReadManager;
    }

    public Channel resolveChannel(SubscribeCommand command) {
        return channelReadManager.getById(command.channelId());
    }
}
