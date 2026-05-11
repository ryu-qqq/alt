package com.ryuqqq.alt.adapter.out.persistence.mysql.channel.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.ChannelJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.QChannelJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.repository.condition.ChannelConditions;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class ChannelQueryDslRepository {

    private static final QChannelJpaEntity CHANNEL = QChannelJpaEntity.channelJpaEntity;

    private final JPAQueryFactory queryFactory;

    public ChannelQueryDslRepository(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    public Optional<ChannelJpaEntity> findById(Long id) {
        return Optional.ofNullable(
            queryFactory.selectFrom(CHANNEL)
                .where(ChannelConditions.idEq(id))
                .fetchOne()
        );
    }

    public boolean existsById(Long id) {
        Integer one = queryFactory.selectOne()
            .from(CHANNEL)
            .where(ChannelConditions.idEq(id))
            .fetchFirst();
        return one != null;
    }

    public List<ChannelJpaEntity> findByIdIn(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return queryFactory.selectFrom(CHANNEL)
            .where(ChannelConditions.idIn(ids))
            .fetch();
    }
}
