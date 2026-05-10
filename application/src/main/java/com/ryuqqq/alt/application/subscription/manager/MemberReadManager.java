package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class MemberReadManager {

    private final MemberQueryPort memberQueryPort;

    public MemberReadManager(MemberQueryPort memberQueryPort) {
        this.memberQueryPort = memberQueryPort;
    }

    @Transactional(readOnly = true)
    public Optional<Member> findByPhoneNumber(PhoneNumber phoneNumber) {
        return memberQueryPort.findByPhoneNumber(phoneNumber);
    }

    @Transactional(readOnly = true)
    public Member getByPhoneNumber(PhoneNumber phoneNumber) {
        return memberQueryPort.findByPhoneNumber(phoneNumber)
            .orElseThrow(() -> new MemberNotFoundException("phoneNumber=" + phoneNumber.value()));
    }
}
