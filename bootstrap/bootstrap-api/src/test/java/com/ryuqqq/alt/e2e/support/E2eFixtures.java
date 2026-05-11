package com.ryuqqq.alt.e2e.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * E2E 테스트용 SQL 시드 헬퍼.
 *
 * - 회원 / 시도 / 요약을 직접 insert 해 시나리오 사전 상태를 만든다.
 * - 채널은 Flyway 가 자동 시드한 1~6번 사용 (홈페이지/모바일앱/네이버/SKT/콜센터/이메일).
 */
public final class E2eFixtures {

    public static final long CHANNEL_HOMEPAGE_ID = 1L;          // BOTH
    public static final long CHANNEL_MOBILE_APP_ID = 2L;        // BOTH
    public static final long CHANNEL_NAVER_ID = 3L;             // SUBSCRIBE_ONLY
    public static final long CHANNEL_SKT_ID = 4L;               // SUBSCRIBE_ONLY
    public static final long CHANNEL_CALL_CENTER_ID = 5L;       // UNSUBSCRIBE_ONLY
    public static final long CHANNEL_EMAIL_ID = 6L;             // UNSUBSCRIBE_ONLY

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public E2eFixtures(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public String randomPhoneNumber() {
        // 010 + 8 random digits, prefixed "1" to avoid leading-zero issues with int formatting.
        int rnd = 10_000_000 + (int) (Math.random() * 89_999_999);
        return "010" + rnd;
    }

    /**
     * member row 직접 insert. 반환값은 auto-increment 된 id.
     * created_at/updated_at 은 명시적으로 채워 — SimpleJdbcInsert 가 PreparedStatement 에 모든 컬럼을
     * 바인딩할 때 DEFAULT 가 무시되는 케이스 회피.
     */
    public long insertMember(String phoneNumber, String status) {
        Timestamp now = Timestamp.from(Instant.now());
        SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
            .withTableName("member")
            .usingColumns("phone_number", "status", "created_at", "updated_at")
            .usingGeneratedKeyColumns("id");
        Number key = insert.executeAndReturnKey(Map.of(
            "phone_number", phoneNumber,
            "status", status,
            "created_at", now,
            "updated_at", now
        ));
        return key.longValue();
    }

    /**
     * COMMITTED subscription_attempt 직접 insert. 반환값은 auto-increment 된 attempt id.
     */
    public long insertCommittedAttempt(
        long memberId,
        long channelId,
        String kind,
        String fromStatus,
        String toStatus,
        Instant occurredAt
    ) {
        Timestamp now = Timestamp.from(Instant.now());
        SimpleJdbcInsert insert = new SimpleJdbcInsert(dataSource)
            .withTableName("subscription_attempt")
            .usingColumns(
                "member_id", "channel_id", "kind", "from_status", "to_status",
                "requested_at", "completed_at", "status", "idempotency_key",
                "created_at", "updated_at"
            )
            .usingGeneratedKeyColumns("id");
        Number key = insert.executeAndReturnKey(Map.ofEntries(
            Map.entry("member_id", memberId),
            Map.entry("channel_id", channelId),
            Map.entry("kind", kind),
            Map.entry("from_status", fromStatus),
            Map.entry("to_status", toStatus),
            Map.entry("requested_at", Timestamp.from(occurredAt)),
            Map.entry("completed_at", Timestamp.from(occurredAt)),
            Map.entry("status", "COMMITTED"),
            Map.entry("idempotency_key", UUID.randomUUID().toString()),
            Map.entry("created_at", now),
            Map.entry("updated_at", now)
        ));
        return key.longValue();
    }

    /**
     * history_summary 직접 insert. created_at/updated_at 도 명시.
     */
    public void insertHistorySummary(long memberId, long fingerprint, String summary) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
            "INSERT INTO history_summary(member_id, fingerprint, summary, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
            memberId, fingerprint, summary, now, now
        );
    }

    /**
     * 단건 member status 조회 (검증용).
     */
    public String selectMemberStatus(String phoneNumber) {
        return jdbc.queryForObject(
            "SELECT status FROM member WHERE phone_number = ?",
            String.class, phoneNumber
        );
    }

    public Long selectMemberId(String phoneNumber) {
        return jdbc.queryForObject(
            "SELECT id FROM member WHERE phone_number = ?",
            Long.class, phoneNumber
        );
    }

    public int countMembers(String phoneNumber) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM member WHERE phone_number = ?",
            Integer.class, phoneNumber
        );
        return c == null ? 0 : c;
    }

    public int countAttempts(long memberId) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_attempt WHERE member_id = ?",
            Integer.class, memberId
        );
        return c == null ? 0 : c;
    }

    public Map<String, Object> selectLatestAttempt(long memberId) {
        return jdbc.queryForMap(
            "SELECT * FROM subscription_attempt WHERE member_id = ? ORDER BY id DESC LIMIT 1",
            memberId
        );
    }

    public Map<String, Object> selectHistorySummary(long memberId) {
        return jdbc.queryForMap(
            "SELECT * FROM history_summary WHERE member_id = ?",
            memberId
        );
    }

    public int countHistorySummary(long memberId) {
        Integer c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM history_summary WHERE member_id = ?",
            Integer.class, memberId
        );
        return c == null ? 0 : c;
    }
}
