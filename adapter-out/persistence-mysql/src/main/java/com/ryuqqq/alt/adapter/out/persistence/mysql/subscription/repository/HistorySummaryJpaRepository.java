package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.HistorySummaryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA — save / findById 만 사용.
 * memberId 가 PK 이므로 save() 가 upsert 로 동작 (merge: PK 존재 시 UPDATE, 없으면 INSERT).
 */
public interface HistorySummaryJpaRepository extends JpaRepository<HistorySummaryJpaEntity, Long> {
}
