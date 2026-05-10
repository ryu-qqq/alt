package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.domain.member.Member;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemberCommandManager {

    private final MemberCommandPort memberCommandPort;

    public MemberCommandManager(MemberCommandPort memberCommandPort) {
        this.memberCommandPort = memberCommandPort;
    }

    @Transactional
    public Long persist(Member member) {
        return memberCommandPort.persist(member);
    }
}
