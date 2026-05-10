package com.ryuqqq.alt.application.subscription.validator;

import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.domain.channel.Channel;
import org.springframework.stereotype.Component;

@Component
public class UnsubscribeValidator {

    private final ChannelReadManager channelReadManager;

    public UnsubscribeValidator(ChannelReadManager channelReadManager) {
        this.channelReadManager = channelReadManager;
    }

    public Channel resolveChannel(UnsubscribeCommand command) {
        return channelReadManager.getById(command.channelId());
    }
}
