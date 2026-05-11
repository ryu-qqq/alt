package com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.member.mapper.MemberEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository.MemberQueryDslRepository;
import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MemberQueryAdapter implements MemberQueryPort {

    private final MemberQueryDslRepository memberQueryDslRepository;
    private final MemberEntityMapper memberEntityMapper;

    public MemberQueryAdapter(MemberQueryDslRepository memberQueryDslRepository, MemberEntityMapper memberEntityMapper) {
        this.memberQueryDslRepository = memberQueryDslRepository;
        this.memberEntityMapper = memberEntityMapper;
    }

    @Override
    public Optional<Member> findById(MemberId id) {
        return memberQueryDslRepository.findById(id.value())
            .map(memberEntityMapper::toDomain);
    }

    @Override
    public Optional<Member> findByPhoneNumber(PhoneNumber phoneNumber) {
        return memberQueryDslRepository.findByPhoneNumber(phoneNumber.value())
            .map(memberEntityMapper::toDomain);
    }

    @Override
    public boolean existsByPhoneNumber(PhoneNumber phoneNumber) {
        return memberQueryDslRepository.existsByPhoneNumber(phoneNumber.value());
    }
}
