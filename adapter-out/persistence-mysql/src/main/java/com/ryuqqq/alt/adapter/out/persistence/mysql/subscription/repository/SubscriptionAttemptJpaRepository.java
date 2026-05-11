package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.SubscriptionAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA — save / saveAll 전용. 조회는 SubscriptionAttemptQueryDslRepository 사용.
 */
public interface SubscriptionAttemptJpaRepository extends JpaRepository<SubscriptionAttemptJpaEntity, Long> {
}
