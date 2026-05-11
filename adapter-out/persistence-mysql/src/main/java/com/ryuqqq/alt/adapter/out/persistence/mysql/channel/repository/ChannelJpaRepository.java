package com.ryuqqq.alt.adapter.out.persistence.mysql.channel.repository;

import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.ChannelJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA — save / saveAll 전용. 조회는 ChannelQueryDslRepository 사용.
 * (현재 도메인은 채널 쓰기가 없지만 마이그레이션/시드 외 추가 시 대비)
 */
public interface ChannelJpaRepository extends JpaRepository<ChannelJpaEntity, Long> {
}
