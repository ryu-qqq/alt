package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.common.factory.TimeProvider;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 구독 UseCase 의 모든 도메인 객체를 한 방에 빌드하는 단일 팩토리.
 *
 * createBundle(command) 호출 한 번으로:
 *   - member  : Member.forNew(phone, NONE) — draft (id=null)
 *   - channel : null — Service 가 channelReadManager 로 fetch 후 withChannel 로 주입
 *   - attempt : SubscriptionAttempt.committed(...) — 낙관적 기대값. memberId=null, channelId 만 박혀 있음
 *
 * 추후 흐름은 SubscribeBundle 의 withMember / withChannel / isRegistrationOnly 메서드 참고.
 */
@Component
public class SubscribeFactory {

    private final TimeProvider timeProvider;

    public SubscribeFactory(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public SubscribeBundle createBundle(SubscribeCommand command) {
        Member member = Member.forNew(command.phoneNumber(), SubscriptionStatus.NONE);

        Instant now = timeProvider.now();
        SubscriptionAttempt attempt = SubscriptionAttempt.committed(
            member.id(), command.channelId(), AttemptKind.SUBSCRIBE,
            member.status(), command.targetStatus(),
            now, now, command.idempotencyKey()
        );

        return new SubscribeBundle(member, null, attempt);
    }
}
