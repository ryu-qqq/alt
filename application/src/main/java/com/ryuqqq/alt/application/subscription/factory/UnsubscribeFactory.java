package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 해지 UseCase 의 모든 도메인 객체를 한 방에 빌드하는 단일 팩토리.
 *
 * createBundle(member, command) 호출 한 번으로:
 *   - member  : 호출자가 사전 조회한 영속 회원 (해지는 신규 등록 경로 없음)
 *   - channel : null — Service 가 channelReadManager 로 fetch 후 withChannel 로 주입
 *   - attempt : SubscriptionAttempt.committed(...) — 낙관적 기대값.
 *               fromStatus = member.status(), toStatus = command.targetStatus()
 *
 * 추후 흐름은 UnsubscribeBundle 의 withChannel / verifyTransition / applyXxx 메서드 참고.
 */
@Component
public class UnsubscribeFactory {

    private final TimeProvider timeProvider;

    public UnsubscribeFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public UnsubscribeBundle createBundle(Member member, UnsubscribeCommand command) {
        Instant now = timeProvider.now();
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            member.id(), command.channelId(), AttemptKind.UNSUBSCRIBE,
            member.status(), command.targetStatus(),
            now, now, command.idempotencyKey()
        );

        return new UnsubscribeBundle(member, null, attempt);
    }
}
