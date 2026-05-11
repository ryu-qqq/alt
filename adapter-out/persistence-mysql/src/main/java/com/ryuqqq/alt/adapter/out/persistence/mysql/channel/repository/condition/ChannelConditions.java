package com.ryuqqq.alt.adapter.out.persistence.mysql.channel.repository.condition;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.QChannelJpaEntity;

import java.util.Collection;

public final class ChannelConditions {

    private static final QChannelJpaEntity CHANNEL = QChannelJpaEntity.channelJpaEntity;

    private ChannelConditions() {
    }

    public static BooleanExpression idEq(Long id) {
        return id != null ? CHANNEL.id.eq(id) : null;
    }

    public static BooleanExpression idIn(Collection<Long> ids) {
        return (ids == null || ids.isEmpty()) ? null : CHANNEL.id.in(ids);
    }
}
