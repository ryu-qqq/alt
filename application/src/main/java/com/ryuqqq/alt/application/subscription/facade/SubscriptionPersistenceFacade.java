package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptCommandPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.AttemptId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 사가의 영속화 묶음.
 *
 * 두 메서드의 의도 차이:
 * - saveWithMemberUpdate: APPROVED 케이스. member 상태 변경 + attempt 동시 영속 (원자적)
 * - saveAttempt        : REJECTED / FAILED 케이스. member 변경 없으므로 attempt 만 영속
 *
 * 반환값:
 * - persist 후 DB 가 채번한 attemptId 를 도메인 객체에 반영한 새 SubscriptionAttempt 를 반환한다.
 *   호출자(Coordinator) 는 이 반환값으로 finalBundle 을 재구성해 응답 DTO 에 attemptId 를 노출한다.
 */
@Component
public class SubscriptionPersistenceFacade {

    private final MemberCommandPort memberCommandPort;
    private final SubscriptionAttemptCommandPort subscriptionAttemptCommandPort;

    public SubscriptionPersistenceFacade(
        MemberCommandPort memberCommandPort,
        SubscriptionAttemptCommandPort subscriptionAttemptCommandPort
    ) {
        this.memberCommandPort = memberCommandPort;
        this.subscriptionAttemptCommandPort = subscriptionAttemptCommandPort;
    }

    @Transactional
    public SubscriptionAttempt saveWithMemberUpdate(Member member, SubscriptionAttempt attempt) {
        memberCommandPort.persist(member);
        Long savedId = subscriptionAttemptCommandPort.persist(attempt);
        return attempt.withId(AttemptId.of(savedId));
    }

    @Transactional
    public SubscriptionAttempt saveAttempt(SubscriptionAttempt attempt) {
        Long savedId = subscriptionAttemptCommandPort.persist(attempt);
        return attempt.withId(AttemptId.of(savedId));
    }
}
