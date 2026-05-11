package com.ryuqqq.alt.adapter.out.persistence.mysql.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Persistence MySQL 어댑터의 JPA scope 명시.
 *
 * <p>bootstrap 진입점이 본 패키지 밖에 위치(예: {@code com.ryuqqq.alt.bootstrap.api})하므로,
 * Spring Data JPA 의 default scan(진입점 패키지부터)이 본 모듈을 발견하지 못한다.
 * 본 Config 가 명시적으로 리포지토리/엔티티 패키지를 선언해 책임을 어댑터 모듈에 캡슐화한다.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.ryuqqq.alt.adapter.out.persistence.mysql")
@EntityScan(basePackages = "com.ryuqqq.alt.adapter.out.persistence.mysql")
public class PersistenceMysqlConfig {
}
