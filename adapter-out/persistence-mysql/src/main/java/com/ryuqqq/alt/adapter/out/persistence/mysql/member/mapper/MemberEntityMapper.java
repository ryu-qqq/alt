package com.ryuqqq.alt.adapter.out.persistence.mysql.member.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.MemberJpaEntity;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import org.springframework.stereotype.Component;

@Component
public class MemberEntityMapper {

    public MemberJpaEntity toEntity(Member member) {
        return MemberJpaEntity.create(
            member.id().value(),
            member.phoneNumber().value(),
            member.status()
        );
    }

    public Member toDomain(MemberJpaEntity entity) {
        return Member.reconstitute(
            MemberId.of(entity.getId()),
            PhoneNumber.of(entity.getPhoneNumber()),
            entity.getStatus()
        );
    }
}
