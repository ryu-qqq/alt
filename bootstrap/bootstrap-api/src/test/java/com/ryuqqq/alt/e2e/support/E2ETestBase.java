package com.ryuqqq.alt.e2e.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.ryuqqq.alt.bootstrap.api.ApiApplication;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import com.ryuqqq.alt.application.subscription.port.out.RandomClient;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * E2E 테스트 베이스.
 *
 * <p>설계 메모:
 * <ul>
 *   <li><b>ApiApplication 풀스택 부팅</b> — Controller → UseCase → Persistence → Cache 까지 실제 빈으로 검증.</li>
 *   <li><b>Testcontainers MySQL 8 singleton</b> — static 블록에서 한 번만 start, JVM 종료시 자동 정리.
 *       모든 테스트 클래스가 컨테이너를 공유 (port 충돌 방지).</li>
 *   <li><b>외부 Port @MockBean</b> — RandomClient(csrng) / LlmSummaryClient(LLM) 둘 다 mock 으로 교체.
 *       실제 어댑터(LlmClientAdapter / NoOpLlmClient / CsrngClientAdapter) 는 컨텍스트에서 mock 으로 대체된다.
 *       WireMock 도입 비용 회피 + 외부 호출 횟수 검증 용이.</li>
 *   <li><b>@Sql 미사용</b> — 각 테스트마다 JdbcTemplate 으로 직접 정리 (FK 순서 + 채널 시드 재삽입 제어).
 *       Flyway 가 띄운 채널 6건은 cleanup 후에도 그대로 유지된다 (TRUNCATE 미수행).</li>
 *   <li><b>Idempotency 캐시 격리</b> — Caffeine cache 빈을 직접 받아 invalidateAll() (@AfterEach).</li>
 * </ul>
 *
 * 하위 테스트 클래스는 이 베이스를 상속하고, {@link #mockMvc}, {@link #objectMapper},
 * {@link #randomClient}, {@link #llmSummaryClient}, {@link #jdbc} 를 그대로 사용한다.
 */
@SpringBootTest(classes = ApiApplication.class, webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
public abstract class E2ETestBase {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0");

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
        .withDatabaseName("alt_e2e")
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

    @MockBean
    protected RandomClient randomClient;

    @MockBean
    protected LlmSummaryClient llmSummaryClient;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired(required = false)
    protected Cache<String, Boolean> idempotencyShortCircuitCache;

    /**
     * 매 테스트 후 멤버/시도/요약 정리 + Caffeine 캐시 invalidate.
     * 채널은 Flyway 시드 6건 유지 (TRUNCATE 하지 않음).
     */
    @AfterEach
    void cleanUp() {
        // FK 순서: history_summary → subscription_attempt → member
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.execute("TRUNCATE TABLE history_summary");
        jdbc.execute("TRUNCATE TABLE subscription_attempt");
        jdbc.execute("TRUNCATE TABLE member");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");

        if (idempotencyShortCircuitCache != null) {
            idempotencyShortCircuitCache.invalidateAll();
        }
    }
}
