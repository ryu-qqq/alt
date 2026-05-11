package com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.MemberJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.mapper.MemberEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository.MemberJpaRepository;
import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.domain.member.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberCommandAdapter implements MemberCommandPort {

    private final MemberJpaRepository memberJpaRepository;
    private final MemberEntityMapper memberEntityMapper;

    public MemberCommandAdapter(MemberJpaRepository memberJpaRepository, MemberEntityMapper memberEntityMapper) {
        this.memberJpaRepository = memberJpaRepository;
        this.memberEntityMapper = memberEntityMapper;
    }

    @Override
    public Long persist(Member member) {
        MemberJpaEntity entity = memberEntityMapper.toEntity(member);
        MemberJpaEntity saved = memberJpaRepository.save(entity);
        return saved.getId();
    }
}
