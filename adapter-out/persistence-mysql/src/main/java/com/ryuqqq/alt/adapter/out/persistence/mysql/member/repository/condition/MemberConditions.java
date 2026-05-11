package com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository.condition;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.QMemberJpaEntity;

/**
 * Member 도메인의 QueryDSL where 조건 모음.
 * - 인자 null → null 반환 → QueryDSL where 절이 자동 무시 (null-safe 합성 가능)
 * - private 생성자: 인스턴스화 금지
 */
public final class MemberConditions {

    private static final QMemberJpaEntity MEMBER = QMemberJpaEntity.memberJpaEntity;

    private MemberConditions() {
    }

    public static BooleanExpression idEq(Long id) {
        return id != null ? MEMBER.id.eq(id) : null;
    }

    public static BooleanExpression phoneNumberEq(String phoneNumber) {
        return phoneNumber != null ? MEMBER.phoneNumber.eq(phoneNumber) : null;
    }
}
