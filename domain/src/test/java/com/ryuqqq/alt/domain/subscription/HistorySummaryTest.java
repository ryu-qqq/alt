package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.member.MemberId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HistorySummary record 검증.
 *
 * 정책:
 * - fingerprint 가 같으면 같은 요약으로 간주 (캐시 키)
 * - record 동등성은 모든 컴포넌트 기반
 */
@DisplayName("HistorySummary — LLM 자연어 이력 요약 VO")
class HistorySummaryTest {

    @Nested
    @DisplayName("T-1. of 팩토리")
    class Factory {

        @Test
        @DisplayName("of 는 모든 컴포넌트를 그대로 보존")
        void ofPreservesAllComponents() {
            MemberId memberId = MemberId.of(7L);

            HistorySummary summary = HistorySummary.of(memberId, 42L, "최근 PREMIUM 으로 업그레이드");

            assertThat(summary.memberId()).isEqualTo(memberId);
            assertThat(summary.fingerprint()).isEqualTo(42L);
            assertThat(summary.summary()).isEqualTo("최근 PREMIUM 으로 업그레이드");
        }
    }

    @Nested
    @DisplayName("T-2. 입력 보존")
    class Preservation {

        @Test
        @DisplayName("빈 summary 도 그대로 보존")
        void emptySummary() {
            HistorySummary summary = HistorySummary.of(MemberId.of(1L), 0L, "");

            assertThat(summary.summary()).isEmpty();
        }

        @Test
        @DisplayName("긴 summary 도 그대로 보존")
        void longSummary() {
            String longText = "이 회원은 최근 한 달 동안 BASIC 으로 업그레이드 후 PREMIUM 까지 전환했고, 두 차례 채널 변경이 있었습니다.";

            HistorySummary summary = HistorySummary.of(MemberId.of(1L), 99L, longText);

            assertThat(summary.summary()).isEqualTo(longText);
        }

        @Test
        @DisplayName("fingerprint=0 (이력 없음) 도 유효")
        void zeroFingerprint() {
            HistorySummary summary = HistorySummary.of(MemberId.of(1L), 0L, "이력 없음");

            assertThat(summary.fingerprint()).isZero();
        }
    }

    @Nested
    @DisplayName("T-6. record 동등성")
    class Equality {

        @Test
        @DisplayName("모든 컴포넌트가 같으면 equals=true")
        void allEqual() {
            HistorySummary a = HistorySummary.of(MemberId.of(1L), 100L, "동일 요약");
            HistorySummary b = HistorySummary.of(MemberId.of(1L), 100L, "동일 요약");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("fingerprint 가 다르면 equals=false (캐시 invalidate 의도)")
        void differentFingerprint() {
            HistorySummary a = HistorySummary.of(MemberId.of(1L), 100L, "요약");
            HistorySummary b = HistorySummary.of(MemberId.of(1L), 101L, "요약");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("memberId 가 다르면 equals=false (회원별 캐시 분리)")
        void differentMember() {
            HistorySummary a = HistorySummary.of(MemberId.of(1L), 100L, "요약");
            HistorySummary b = HistorySummary.of(MemberId.of(2L), 100L, "요약");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("summary 만 달라도 equals=false (record 의미)")
        void differentSummary() {
            HistorySummary a = HistorySummary.of(MemberId.of(1L), 100L, "요약 A");
            HistorySummary b = HistorySummary.of(MemberId.of(1L), 100L, "요약 B");

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("T-7. SubscriptionHistory.latestCommittedAttemptId 와의 페어링")
    class FingerprintPairing {

        @Test
        @DisplayName("같은 회원의 이력 fingerprint 와 요약 fingerprint 가 일치하면 같은 캐시 엔트리")
        void matchesLatestCommittedAttemptId() {
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();
            long fingerprint = history.latestCommittedAttemptId();

            HistorySummary summary = HistorySummary.of(history.memberId(), fingerprint, "최신 상태 요약");

            assertThat(summary.memberId()).isEqualTo(history.memberId());
            assertThat(summary.fingerprint()).isEqualTo(fingerprint);
        }

        @Test
        @DisplayName("이력이 비어있으면 fingerprint=0 인 요약과 페어링")
        void emptyHistoryPairsWithZero() {
            SubscriptionHistory empty = SubscriptionHistoryFixture.empty();

            HistorySummary summary = HistorySummary.of(empty.memberId(), empty.latestCommittedAttemptId(), "이력 없음");

            assertThat(summary.fingerprint()).isZero();
        }
    }
}
