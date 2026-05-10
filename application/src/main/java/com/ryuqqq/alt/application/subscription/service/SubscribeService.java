package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.csrng.CsrngOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionPersistenceFacade;
import com.ryuqqq.alt.application.subscription.factory.MemberFactory;
import com.ryuqqq.alt.application.subscription.factory.SubscriptionAttemptFactory;
import com.ryuqqq.alt.application.subscription.manager.CsrngClientManager;
import com.ryuqqq.alt.application.subscription.manager.MemberReadManager;
import com.ryuqqq.alt.application.subscription.manager.SubscriptionAttemptReadManager;
import com.ryuqqq.alt.application.subscription.port.in.SubscribeUseCase;
import com.ryuqqq.alt.application.subscription.validator.SubscribeValidator;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.policy.SubscriptionTransitionPolicy;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 구독 사가 흐름의 오케스트레이터.
 *
 * 흐름 (ADR-0002):
 *   1) 멱등성 키로 기존 시도 조회 → terminal 이면 동일 응답 반환 (캐시)
 *   2) 채널 검증 (Validator)
 *   3) 회원 조회 — 없으면 신규 생성 (NONE 상태)
 *   4) 도메인 정책 검증 (TransitionPolicy)
 *   5) PENDING SubscriptionAttempt 생성 + Member + Attempt 원자 저장 (Facade)
 *   6) csrng 호출 (트랜잭션 밖, 어댑터에서 Resilience4j 적용)
 *   7) 결과에 따라 commit / rollback / fail 후 Facade.applyResult 로 영속
 */
@Service
public class SubscribeService implements SubscribeUseCase {

    private final SubscribeValidator subscribeValidator;
    private final MemberReadManager memberReadManager;
    private final MemberFactory memberFactory;
    private final SubscriptionAttemptFactory subscriptionAttemptFactory;
    private final SubscriptionAttemptReadManager subscriptionAttemptReadManager;
    private final SubscriptionPersistenceFacade subscriptionPersistenceFacade;
    private final CsrngClientManager csrngClientManager;
    private final TimeProvider timeProvider;

    public SubscribeService(
        SubscribeValidator subscribeValidator,
        MemberReadManager memberReadManager,
        MemberFactory memberFactory,
        SubscriptionAttemptFactory subscriptionAttemptFactory,
        SubscriptionAttemptReadManager subscriptionAttemptReadManager,
        SubscriptionPersistenceFacade subscriptionPersistenceFacade,
        CsrngClientManager csrngClientManager,
        TimeProvider timeProvider
    ) {
        this.subscribeValidator = subscribeValidator;
        this.memberReadManager = memberReadManager;
        this.memberFactory = memberFactory;
        this.subscriptionAttemptFactory = subscriptionAttemptFactory;
        this.subscriptionAttemptReadManager = subscriptionAttemptReadManager;
        this.subscriptionPersistenceFacade = subscriptionPersistenceFacade;
        this.csrngClientManager = csrngClientManager;
        this.timeProvider = timeProvider;
    }

    @Override
    public SubscribeResult execute(SubscribeCommand command) {
        // 1) 멱등성 캐시 (ADR-0004)
        Optional<SubscriptionAttempt> existing = subscriptionAttemptReadManager
            .findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent() && existing.get().isTerminal()) {
            SubscriptionAttempt cached = existing.get();
            Member member = memberReadManager.getByPhoneNumber(command.phoneNumber());
            return SubscribeResult.from(cached, member.status());
        }

        // 2) 채널 검증
        Channel channel = subscribeValidator.resolveChannel(command);

        // 3) 회원 조회 또는 신규 생성
        Member member = memberReadManager.findByPhoneNumber(command.phoneNumber())
            .orElseGet(() -> memberFactory.createNew(command.phoneNumber()));

        // 4) 도메인 정책 검증 (채널 권한 + 상태 전이)
        SubscriptionTransitionPolicy.verifySubscribe(member, channel, command.targetStatus());

        // 5) PENDING attempt 생성 + 원자 저장
        SubscriptionAttempt attempt = subscriptionAttemptFactory.forSubscribe(
            member.id(),
            channel.id(),
            member.status(),
            command.targetStatus(),
            command.idempotencyKey()
        );
        subscriptionPersistenceFacade.saveNewAttempt(member, attempt);

        // 6) csrng 호출 (트랜잭션 밖)
        CsrngOutcome outcome = csrngClientManager.fetchRandom();

        // 7) 결과 적용
        Instant now = timeProvider.now();
        switch (outcome) {
            case CsrngOutcome.Success success -> {
                if (success.isCommitSignal()) {
                    member.applySubscribe(command.targetStatus());
                    attempt.commit(now);
                } else {
                    attempt.rollback(now);
                }
            }
            case CsrngOutcome.Unavailable ignored -> attempt.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, now);
        }
        subscriptionPersistenceFacade.applyResult(member, attempt);

        return SubscribeResult.from(attempt, member.status());
    }
}
