package com.ryuqqq.alt.application.subscription.port.out;

import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;

import java.util.Optional;

public interface MemberQueryPort {

    Optional<Member> findById(MemberId id);

    Optional<Member> findByPhoneNumber(PhoneNumber phoneNumber);

    boolean existsByPhoneNumber(PhoneNumber phoneNumber);
}
