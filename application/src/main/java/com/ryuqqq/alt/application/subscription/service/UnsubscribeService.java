package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.csrng.CsrngOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.SubscriptionAttemptFactory;
import com.ryuqqq.alt.application.subscription.manager.CsrngClientManager;
import com.ryuqqq.alt.application.subscription.manager.MemberReadManager;
import com.ryuqqq.alt.application.subscription.manager.SubscriptionAttemptReadManager;
import com.ryuqqq.alt.application.subscription.port.in.UnsubscribeUseCase;
import com.ryuqqq.alt.application.subscription.validator.UnsubscribeValidator;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.policy.SubscriptionTransitionPolicy;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 해지 사가 흐름. 구독과 달리 회원이 반드시 존재해야 한다 (이미 가입된 회원만 해지 가능).
 */
@Service
public class UnsubscribeService implements UnsubscribeUseCase {

    private final UnsubscribeValidator unsubscribeValidator;
    private final MemberReadManager memberReadManager;
    private final SubscriptionAttemptFactory subscriptionAttemptFactory;
    private final SubscriptionAttemptReadManager subscriptionAttemptReadManager;
    private final SubscriptionPersistenceFacade subscriptionPersistenceFacade;
    private final CsrngClientManager csrngClientManager;
    private final TimeProvider timeProvider;

    public UnsubscribeService(
        UnsubscribeValidator unsubscribeValidator,
        MemberReadManager memberReadManager,
        SubscriptionAttemptFactory subscriptionAttemptFactory,
        SubscriptionAttemptReadManager subscriptionAttemptReadManager,
        SubscriptionPersistenceFacade subscriptionPersistenceFacade,
        CsrngClientManager csrngClientManager,
        TimeProvider timeProvider
    ) {
        this.unsubscribeValidator = unsubscribeValidator;
        this.memberReadManager = memberReadManager;
        this.subscriptionAttemptFactory = subscriptionAttemptFactory;
        this.subscriptionAttemptReadManager = subscriptionAttemptReadManager;
        this.subscriptionPersistenceFacade = subscriptionPersistenceFacade;
        this.csrngClientManager = csrngClientManager;
        this.timeProvider = timeProvider;
    }

    @Override
    public UnsubscribeResult execute(UnsubscribeCommand command) {
        Optional<SubscriptionAttempt> existing = subscriptionAttemptReadManager
            .findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent() && existing.get().isTerminal()) {
            SubscriptionAttempt cached = existing.get();
            Member member = memberReadManager.getByPhoneNumber(command.phoneNumber());
            return UnsubscribeResult.from(cached, member.status());
        }

        Channel channel = unsubscribeValidator.resolveChannel(command);
        Member member = memberReadManager.getByPhoneNumber(command.phoneNumber());

        SubscriptionTransitionPolicy.verifyUnsubscribe(member, channel, command.targetStatus());

        SubscriptionAttempt attempt = subscriptionAttemptFactory.forUnsubscribe(
            member.id(),
            channel.id(),
            member.status(),
            command.targetStatus(),
            command.idempotencyKey()
        );
        subscriptionPersistenceFacade.saveNewAttempt(member, attempt);

        CsrngOutcome outcome = csrngClientManager.fetchRandom();

        Instant now = timeProvider.now();
        switch (outcome) {
            case CsrngOutcome.Success success -> {
                if (success.isCommitSignal()) {
                    member.applyUnsubscribe(command.targetStatus());
                    attempt.commit(now);
                } else {
                    attempt.rollback(now);
                }
            }
            case CsrngOutcome.Unavailable ignored -> attempt.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, now);
        }
        subscriptionPersistenceFacade.applyResult(member, attempt);

        return UnsubscribeResult.from(attempt, member.status());
    }
}
