package com.ryuqqq.alt.adapter.out.persistence.mysql;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Persistence 통합 테스트 베이스.
 *
 * <p>설계 메모:
 * <ul>
 *   <li><b>Testcontainers MySQL 8</b> — singleton 컨테이너 (static + 모든 테스트 공유). JVM 종료 시 자동 정리.
 *       @Testcontainers 어노테이션 대신 static 블록에서 직접 start() — 한 번 띄우고 모든 테스트 클래스가 재사용.</li>
 *   <li><b>@DynamicPropertySource</b> — 컨테이너가 노출한 JDBC URL/계정을 Spring 환경에 주입.</li>
 *   <li><b>Flyway</b> — Spring Boot 자동 구성으로 컨테이너 기동 직후 마이그레이션 적용.</li>
 *   <li><b>@Transactional</b> — 각 테스트 메서드 단위 롤백. (단, FK/UNIQUE 충돌처럼 트랜잭션 자체가 무너지는
 *       시나리오는 별도 @Transactional(propagation=NEVER) 처리 또는 hibernate.session_factory.statement_inspector 필요.
 *       단순 검증에는 기본 롤백으로 충분.)</li>
 *   <li><b>슬라이스</b> — {@link PersistenceTestConfiguration} 으로 persistence-mysql 패키지만 로드.
 *       bootstrap 모듈을 끌어들이지 않음.</li>
 * </ul>
 */
@SpringBootTest(classes = PersistenceTestConfiguration.class)
@ActiveProfiles("test")
@Transactional
public abstract class AbstractPersistenceIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0");

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName("alt_test")
        .withUsername("alt")
        .withPassword("alt");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerMysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }
}
