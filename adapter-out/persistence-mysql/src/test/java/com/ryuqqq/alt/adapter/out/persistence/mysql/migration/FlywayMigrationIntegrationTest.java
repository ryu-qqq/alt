package com.ryuqqq.alt.adapter.out.persistence.mysql.migration;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 마이그레이션 검증.
 *
 * - Testcontainers 가 MySQL 컨테이너를 띄우고, Spring Boot 가 Flyway 를 통해 V20260510, V20260511 을 적용한다.
 * - 검증 항목:
 *     1) 4개 테이블(member, channel, subscription_attempt, history_summary) 이 모두 생성됨
 *     2) channel 시드 6건이 INSERT 됨 (BOTH/SUBSCRIBE_ONLY/UNSUBSCRIBE_ONLY 각 2건)
 *     3) FK 제약이 적용되어 있음 (information_schema 로 확인)
 */
@DisplayName("Flyway 마이그레이션 통합 테스트")
class FlywayMigrationIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 가 적용된 마이그레이션 2건이 flyway_schema_history 에 기록되어 있다")
    void flywayHistory_containsTwoVersions() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
            Integer.class
        );

        assertThat(count).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("필수 테이블 4건이 모두 생성되어 있다")
    void allRequiredTablesCreated() {
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() " +
                "  AND table_name IN ('member', 'channel', 'subscription_attempt', 'history_summary')",
            String.class
        );

        assertThat(tables).containsExactlyInAnyOrder(
            "member", "channel", "subscription_attempt", "history_summary"
        );
    }

    @Test
    @DisplayName("channel 시드 6건이 INSERT 되어 있고 타입 분포가 BOTH/SUBSCRIBE_ONLY/UNSUBSCRIBE_ONLY 각 2건이다")
    void channelSeedRowsAreInserted() {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM channel", Integer.class);
        assertThat(total).isEqualTo(6);

        List<Map<String, Object>> typeCounts = jdbcTemplate.queryForList(
            "SELECT type, COUNT(*) AS cnt FROM channel GROUP BY type"
        );

        assertThat(typeCounts).hasSize(3);
        for (Map<String, Object> row : typeCounts) {
            assertThat(((Number) row.get("cnt")).intValue()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("subscription_attempt 에 member / channel FK 가 등록되어 있다")
    void subscriptionAttemptHasForeignKeys() {
        List<String> fkNames = jdbcTemplate.queryForList(
            "SELECT constraint_name FROM information_schema.table_constraints " +
                "WHERE table_schema = DATABASE() " +
                "  AND table_name = 'subscription_attempt' " +
                "  AND constraint_type = 'FOREIGN KEY'",
            String.class
        );

        assertThat(fkNames).contains("fk_attempt_member", "fk_attempt_channel");
    }

    @Test
    @DisplayName("history_summary 에 member FK 가 등록되어 있다")
    void historySummaryHasForeignKey() {
        List<String> fkNames = jdbcTemplate.queryForList(
            "SELECT constraint_name FROM information_schema.table_constraints " +
                "WHERE table_schema = DATABASE() " +
                "  AND table_name = 'history_summary' " +
                "  AND constraint_type = 'FOREIGN KEY'",
            String.class
        );

        assertThat(fkNames).contains("fk_history_summary_member");
    }

    @Test
    @DisplayName("member 테이블에 phone_number UNIQUE 제약이 있다")
    void memberHasPhoneNumberUniqueConstraint() {
        List<String> uniqueIndexNames = jdbcTemplate.queryForList(
            "SELECT DISTINCT index_name FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() " +
                "  AND table_name = 'member' " +
                "  AND non_unique = 0 " +
                "  AND index_name <> 'PRIMARY'",
            String.class
        );

        assertThat(uniqueIndexNames).contains("uk_member_phone");
    }

    @Test
    @DisplayName("subscription_attempt 에 idempotency_key UNIQUE 제약이 있다")
    void subscriptionAttemptHasIdempotencyKeyUniqueConstraint() {
        List<String> uniqueIndexNames = jdbcTemplate.queryForList(
            "SELECT DISTINCT index_name FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() " +
                "  AND table_name = 'subscription_attempt' " +
                "  AND non_unique = 0 " +
                "  AND index_name <> 'PRIMARY'",
            String.class
        );

        assertThat(uniqueIndexNames).contains("uk_attempt_idempotency");
    }
}
