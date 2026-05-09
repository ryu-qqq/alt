package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionHistory 컬렉션 VO")
class SubscriptionHistoryTest {

    private static final MemberId MEMBER = MemberId.of(1L);
    private static final ChannelId CHANNEL = ChannelId.of(10L);

    private SubscriptionAttempt committed(SubscriptionStatus from, SubscriptionStatus to, Instant requestedAt, AttemptKind kind) {
        SubscriptionAttempt a = SubscriptionAttempt.forNew(
            MEMBER, CHANNEL, kind, from, to, requestedAt, null
        );
        a.commit(requestedAt.plusSeconds(1));
        return a;
    }

    private SubscriptionAttempt rolledBack(SubscriptionStatus from, SubscriptionStatus to, Instant requestedAt) {
        SubscriptionAttempt a = SubscriptionAttempt.forNew(
            MEMBER, CHANNEL, AttemptKind.SUBSCRIBE, from, to, requestedAt, null
        );
        a.rollback(requestedAt.plusSeconds(1));
        return a;
    }

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("empty: 빈 이력의 currentStatus는 NONE")
        void emptyStartsAtNone() {
            SubscriptionHistory history = SubscriptionHistory.empty(MEMBER);

            assertThat(history.isEmpty()).isTrue();
            assertThat(history.size()).isZero();
            assertThat(history.currentStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(history.latestCommittedChange()).isEmpty();
        }

        @Test
        @DisplayName("입력된 attempts는 requestedAt 오름차순으로 정렬되어 보관")
        void sortsByRequestedAt() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
            Instant t3 = Instant.parse("2026-03-01T00:00:00Z");

            SubscriptionAttempt a1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt a3 = committed(SubscriptionStatus.PREMIUM, SubscriptionStatus.BASIC, t3, AttemptKind.UNSUBSCRIBE);
            SubscriptionAttempt a2 = committed(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2, AttemptKind.SUBSCRIBE);

            // 일부러 뒤섞어 입력
            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(a3, a1, a2));

            assertThat(history.attempts()).containsExactly(a1, a2, a3);
        }
    }

    @Nested
    @DisplayName("T-4. 도메인 로직 — 현재 상태 derive")
    class CurrentStatus {

        @Test
        @DisplayName("최신 COMMITTED의 toStatus가 currentStatus")
        void latestCommittedDecidesCurrent() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");

            SubscriptionAttempt a1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt a2 = committed(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2, AttemptKind.SUBSCRIBE);

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(a1, a2));

            assertThat(history.currentStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("ROLLED_BACK / FAILED는 현재 상태 산출에서 제외")
        void nonCommittedExcluded() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
            Instant t3 = Instant.parse("2026-03-01T00:00:00Z");

            SubscriptionAttempt commit1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt rollback = rolledBack(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2);
            SubscriptionAttempt failed = SubscriptionAttempt.forNew(
                MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t3, null
            );
            failed.fail(AttemptFailureReason.CSRNG_UNAVAILABLE, t3.plusSeconds(1));

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(commit1, rollback, failed));

            assertThat(history.currentStatus()).isEqualTo(SubscriptionStatus.BASIC); // last COMMITTED
            assertThat(history.committedChanges()).containsExactly(commit1);
        }
    }

    @Nested
    @DisplayName("T-4. 사용자 노출용 — committedChanges / latestCommittedChange")
    class UserFacing {

        @Test
        @DisplayName("committedChanges는 COMMITTED만, 시간 오름차순")
        void committedChangesOnly() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
            Instant t3 = Instant.parse("2026-03-01T00:00:00Z");

            SubscriptionAttempt c1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt rb = rolledBack(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2);
            SubscriptionAttempt c3 = committed(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t3, AttemptKind.SUBSCRIBE);

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(c1, rb, c3));

            assertThat(history.committedChanges()).containsExactly(c1, c3);
        }

        @Test
        @DisplayName("latestCommittedChange는 가장 최근 커밋")
        void latestCommitted() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");

            SubscriptionAttempt c1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt c2 = committed(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2, AttemptKind.SUBSCRIBE);

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(c1, c2));

            Optional<SubscriptionAttempt> latest = history.latestCommittedChange();
            assertThat(latest).contains(c2);
        }
    }

    @Nested
    @DisplayName("T-4. since — 시간 슬라이스")
    class Slicing {

        @Test
        @DisplayName("since는 from 시점 이후만 포함")
        void sliceSince() {
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");
            Instant t3 = Instant.parse("2026-03-01T00:00:00Z");

            SubscriptionAttempt a1 = committed(SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt a2 = committed(SubscriptionStatus.BASIC, SubscriptionStatus.PREMIUM, t2, AttemptKind.SUBSCRIBE);
            SubscriptionAttempt a3 = committed(SubscriptionStatus.PREMIUM, SubscriptionStatus.BASIC, t3, AttemptKind.UNSUBSCRIBE);

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(a1, a2, a3));

            SubscriptionHistory sliced = history.since(t2);

            assertThat(sliced.attempts()).containsExactly(a2, a3);
        }
    }
}
