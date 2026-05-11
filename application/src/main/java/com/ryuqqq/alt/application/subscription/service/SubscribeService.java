package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.coordinator.MemberRegistrationCoordinator;
import com.ryuqqq.alt.application.subscription.coordinator.SubscribeCoordinator;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.factory.SubscribeBundle;
import com.ryuqqq.alt.application.subscription.factory.SubscribeFactory;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.port.in.SubscribeUseCase;
import com.ryuqqq.alt.application.subscription.port.out.IdempotencyRegistryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import org.springframework.stereotype.Service;

@Service
public class SubscribeService implements SubscribeUseCase {

    private final SubscribeFactory subscribeFactory;
    private final MemberRegistrationCoordinator memberRegistrationCoordinator;
    private final ChannelReadManager channelReadManager;
    private final SubscribeCoordinator subscribeCoordinator;
    private final IdempotencyRegistryPort idempotencyRegistry;

    public SubscribeService(
        SubscribeFactory subscribeFactory,
        MemberRegistrationCoordinator memberRegistrationCoordinator,
        ChannelReadManager channelReadManager,
        SubscribeCoordinator subscribeCoordinator,
        IdempotencyRegistryPort idempotencyRegistry
    ) {
        this.subscribeFactory = subscribeFactory;
        this.memberRegistrationCoordinator = memberRegistrationCoordinator;
        this.channelReadManager = channelReadManager;
        this.subscribeCoordinator = subscribeCoordinator;
        this.idempotencyRegistry = idempotencyRegistry;
    }

    @Override
    public SubscribeResult execute(SubscribeCommand command) {
        return idempotencyRegistry.executeOnce(command.idempotencyKey(), () -> {
            SubscribeBundle initial = subscribeFactory.createBundle(command);
            Member persistedMember = memberRegistrationCoordinator.findOrRegister(initial.member());
            SubscribeBundle bundle = initial.withMember(persistedMember);

            if (bundle.isRegistrationOnly()) {
                return SubscribeResult.registrationOnly(bundle.memberStatus());
            }

            Channel channel = channelReadManager.getById(command.channelId());
            SubscribeBundle ready = bundle.withChannel(channel);
            ready.verifyTransition();
            return subscribeCoordinator.coordinate(ready);
        });
    }
}
