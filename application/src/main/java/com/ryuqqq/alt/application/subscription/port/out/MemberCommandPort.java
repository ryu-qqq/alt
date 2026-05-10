package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.member.Member;

public interface MemberCommandPort {

    /**
     * Member 를 영속화한다. id 가 신규(isNew)면 INSERT, 아니면 UPDATE.
     * @return 영속화된 Member 의 식별자(value)
     */
    Long persist(Member member);
}
