package com.ryuqqq.alt.adapter.out.persistence.mysql.member.repository;

import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA — save / saveAll 전용. 조회는 MemberQueryDslRepository 사용.
 */
public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, Long> {
}
