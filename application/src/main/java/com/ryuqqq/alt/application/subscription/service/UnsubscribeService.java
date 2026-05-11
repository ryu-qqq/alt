package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.coordinator.UnsubscribeCoordinator;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.UnsubscribeFactory;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.manager.MemberReadManager;
import com.ryuqqq.alt.application.subscription.port.in.UnsubscribeUseCase;
import com.ryuqqq.alt.application.subscription.port.out.IdempotencyRegistryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import org.springframework.stereotype.Service;

@Service
public class UnsubscribeService implements UnsubscribeUseCase {

    private final UnsubscribeFactory unsubscribeFactory;
    private final MemberReadManager memberReadManager;
    private final ChannelReadManager channelReadManager;
    private final UnsubscribeCoordinator unsubscribeCoordinator;
    private final IdempotencyRegistryPort idempotencyRegistry;

    public UnsubscribeService(
        UnsubscribeFactory unsubscribeFactory,
        MemberReadManager memberReadManager,
        ChannelReadManager channelReadManager,
        UnsubscribeCoordinator unsubscribeCoordinator,
        IdempotencyRegistryPort idempotencyRegistry
    ) {
        this.unsubscribeFactory = unsubscribeFactory;
        this.memberReadManager = memberReadManager;
        this.channelReadManager = channelReadManager;
        this.unsubscribeCoordinator = unsubscribeCoordinator;
        this.idempotencyRegistry = idempotencyRegistry;
    }

    @Override
    public UnsubscribeResult execute(UnsubscribeCommand command) {
        return idempotencyRegistry.executeOnce(command.idempotencyKey(), () -> {
            Member member = memberReadManager.getByPhoneNumber(command.phoneNumber());
            Channel channel = channelReadManager.getById(command.channelId());
            UnsubscribeBundle bundle = unsubscribeFactory.createBundle(member, command).withChannel(channel);
            bundle.verifyTransition();
            return unsubscribeCoordinator.coordinate(bundle);
        });
    }
}
