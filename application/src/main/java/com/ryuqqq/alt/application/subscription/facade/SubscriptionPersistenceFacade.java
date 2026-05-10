package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptCommandPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사가 흐름에서 Member + SubscriptionAttempt 를 한 트랜잭션으로 원자적 저장.
 *
 * 두 메서드는 명세상 의도가 다르다:
 * - saveNewAttempt(...) : PENDING 시도 진입 — 외부 API 호출 직전. Member 가 신규일 수 있다.
 * - applyResult(...)   : csrng 결과 적용 — 외부 API 호출 직후. Member 상태가 변할 수 있다.
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

    /**
     * Member 와 PENDING SubscriptionAttempt 를 같은 트랜잭션으로 저장.
     * - Member 는 신규(isNew)면 INSERT, 아니면 그대로 패스 (현재 상태 변경 없음).
     * - Attempt 는 항상 신규.
     */
    @Transactional
    public PersistedAttempt saveNewAttempt(Member member, SubscriptionAttempt attempt) {
        Long memberId = memberCommandPort.persist(member);
        Long attemptId = subscriptionAttemptCommandPort.persist(attempt);
        return new PersistedAttempt(memberId, attemptId);
    }

    /**
     * 사가 결과 적용. Member 상태와 Attempt 상태를 같은 트랜잭션으로 저장.
     * - 호출자가 commit / rollback / fail 후 도메인 객체 상태를 갱신해 두어야 한다.
     * - Member 상태가 변하지 않은 경우(rollback / fail)에도 멤버 영속화를 호출하면
     *   adapter 가 변화 없음을 감지해 update 하지 않거나 동일 row 로 merge 한다.
     */
    @Transactional
    public void applyResult(Member member, SubscriptionAttempt attempt) {
        memberCommandPort.persist(member);
        subscriptionAttemptCommandPort.persist(attempt);
    }

    public record PersistedAttempt(Long memberId, Long attemptId) { }
}
