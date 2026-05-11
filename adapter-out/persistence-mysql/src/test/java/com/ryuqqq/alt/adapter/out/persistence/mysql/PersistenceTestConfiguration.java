package com.ryuqqq.alt.adapter.out.persistence.mysql;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * persistence-mysql 모듈 전용 통합 테스트 슬라이스.
 *
 * - 전체 애플리케이션을 띄우지 않고, persistence-mysql 패키지의
 *   Entity / JpaRepository / QueryDsl / Mapper / Adapter / @Configuration 만 로드한다.
 * - Flyway, JPA, DataSource, Hibernate 등 Spring Boot 자동 구성은 그대로 활성화.
 * - QueryDslConfig / JpaAuditingConfig / PersistenceMysqlConfig (EnableJpaRepositories + EntityScan 선언) 가
 *   ComponentScan 에 의해 자동으로 잡힌다. 본 슬라이스에 중복 선언하면 BeanDefinitionOverrideException 발생.
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.ryuqqq.alt.adapter.out.persistence.mysql")
public class PersistenceTestConfiguration {
}
