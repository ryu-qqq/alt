package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionHistory 의 조회 메서드 — committedAttempts / committedChannelIds /
 * hasCommitted / latestCommittedAttemptId 동작 검증.
 *
 * 픽스쳐(SubscriptionHistoryFixture)로 시나리오를 통일해 테스트마다 데이터를 새로 만들지 않는다.
 */
@DisplayName("SubscriptionHistory 조회 메서드")
class SubscriptionHistoryQueryTest {

    @Nested
    @DisplayName("committedAttempts")
    class CommittedAttempts {

        @Test
        @DisplayName("COMMITTED 만 추출한다")
        void onlyCommitted() {
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();

            assertThat(history.committedAttempts())
                .hasSize(2)
                .allMatch(SubscriptionAttempt::isCommitted);
        }

        @Test
        @DisplayName("입력 순서를 유지한다 (어댑터 정렬 보존)")
        void preservesOrder() {
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();

            assertThat(history.committedAttempts())
                .extracting(SubscriptionAttempt::idValue)
                .containsExactly(1L, 4L);
        }

        @Test
        @DisplayName("COMMITTED 가 없으면 빈 리스트")
        void noneCommitted() {
            SubscriptionHistory history = SubscriptionHistoryFixture.onlyNonCommitted();

            assertThat(history.committedAttempts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("committedChannelIds")
    class CommittedChannelIds {

        @Test
        @DisplayName("COMMITTED 가 사용한 채널 ID 집합 반환 (중복 제거)")
        void distinctChannelIds() {
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();

            assertThat(history.committedChannelIds())
                .containsExactlyInAnyOrder(ChannelId.of(11L), ChannelId.of(12L));
        }

        @Test
        @DisplayName("COMMITTED 없으면 빈 집합")
        void emptyWhenNoneCommitted() {
            SubscriptionHistory history = SubscriptionHistoryFixture.onlyNonCommitted();

            assertThat(history.committedChannelIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("hasCommitted")
    class HasCommitted {

        @Test
        @DisplayName("COMMITTED 가 하나라도 있으면 true")
        void anyCommitted() {
            assertThat(SubscriptionHistoryFixture.mixedStatuses().hasCommitted()).isTrue();
            assertThat(SubscriptionHistoryFixture.singleCommitted().hasCommitted()).isTrue();
        }

        @Test
        @DisplayName("COMMITTED 가 없으면 false")
        void noneCommitted() {
            assertThat(SubscriptionHistoryFixture.empty().hasCommitted()).isFalse();
            assertThat(SubscriptionHistoryFixture.onlyNonCommitted().hasCommitted()).isFalse();
        }
    }

    @Nested
    @DisplayName("latestCommittedAttemptId")
    class LatestCommittedAttemptId {

        @Test
        @DisplayName("COMMITTED 중 가장 큰 attemptId 를 반환")
        void maxOfCommitted() {
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();

            assertThat(history.latestCommittedAttemptId()).isEqualTo(4L);
        }

        @Test
        @DisplayName("ROLLED_BACK / FAILED 는 무시한다")
        void ignoresNonCommitted() {
            SubscriptionHistory history = SubscriptionHistoryFixture.onlyNonCommitted();

            assertThat(history.latestCommittedAttemptId()).isZero();
        }

        @Test
        @DisplayName("이력이 비어있으면 0")
        void zeroWhenEmpty() {
            assertThat(SubscriptionHistoryFixture.empty().latestCommittedAttemptId()).isZero();
        }
    }
}
