package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.springframework.stereotype.Component;

/**
 * Member 신규 생성. 명세상 최초 회원의 초기 상태는 NONE 으로 시작하고,
 * 첫 구독은 SubscribeService 의 saga 흐름이 NONE → target 으로 전이한다.
 *
 * 시간 의존이 없으므로 TimeProvider 미주입 (Member 도메인이 createdAt 을 보유하지 않음).
 */
@Component
public class MemberFactory {

    public Member createNew(PhoneNumber phoneNumber) {
        return Member.forNew(phoneNumber, SubscriptionStatus.NONE);
    }
}
