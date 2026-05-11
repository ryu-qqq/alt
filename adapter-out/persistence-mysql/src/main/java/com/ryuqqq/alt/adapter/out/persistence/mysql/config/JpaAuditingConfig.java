package com.ryuqqq.alt.adapter.out.persistence.mysql.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseAuditEntity 의 @CreatedDate / @LastModifiedDate 자동 채움.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
