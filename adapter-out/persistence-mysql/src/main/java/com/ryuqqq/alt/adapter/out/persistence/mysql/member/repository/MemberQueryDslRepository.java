package com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.MemberJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.QMemberJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository.condition.MemberConditions;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemberQueryDslRepository {

    private static final QMemberJpaEntity MEMBER = QMemberJpaEntity.memberJpaEntity;

    private final JPAQueryFactory queryFactory;

    public MemberQueryDslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Optional<MemberJpaEntity> findById(Long id) {
        return Optional.ofNullable(
            queryFactory.selectFrom(MEMBER)
                .where(MemberConditions.idEq(id))
                .fetchOne()
        );
    }

    public Optional<MemberJpaEntity> findByPhoneNumber(String phoneNumber) {
        return Optional.ofNullable(
            queryFactory.selectFrom(MEMBER)
                .where(MemberConditions.phoneNumberEq(phoneNumber))
                .fetchOne()
        );
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        Integer one = queryFactory.selectOne()
            .from(MEMBER)
            .where(MemberConditions.phoneNumberEq(phoneNumber))
            .fetchFirst();
        return one != null;
    }
}
