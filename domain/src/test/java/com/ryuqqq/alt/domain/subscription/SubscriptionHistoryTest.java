package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SubscriptionHistory 컬렉션 VO")
class SubscriptionHistoryTest {

    private static final MemberId MEMBER = MemberId.of(1L);
    private static final ChannelId CHANNEL = ChannelId.of(10L);

    private SubscriptionAttempt anyAttempt(Instant requestedAt) {
        return SubscriptionAttempt.forNew(
            MEMBER, CHANNEL, AttemptKind.SUBSCRIBE,
            SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
            requestedAt, null
        );
    }

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("empty: 빈 이력")
        void emptyHistory() {
            SubscriptionHistory history = SubscriptionHistory.empty(MEMBER);

            assertThat(history.isEmpty()).isTrue();
            assertThat(history.size()).isZero();
            assertThat(history.attempts()).isEmpty();
            assertThat(history.memberId()).isEqualTo(MEMBER);
        }

        @Test
        @DisplayName("of: 입력 순서를 그대로 보존 (정렬은 영속 어댑터가 담당)")
        void preservesInsertionOrder() {
            Instant t3 = Instant.parse("2026-03-01T00:00:00Z");
            Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant t2 = Instant.parse("2026-02-01T00:00:00Z");

            SubscriptionAttempt a3 = anyAttempt(t3);
            SubscriptionAttempt a1 = anyAttempt(t1);
            SubscriptionAttempt a2 = anyAttempt(t2);

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(a3, a1, a2));

            assertThat(history.attempts()).containsExactly(a3, a1, a2);
            assertThat(history.size()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("T-3. 불변식")
    class Invariants {

        @Test
        @DisplayName("외부에서 입력 리스트를 변경해도 VO 내부는 영향 없음 (defensive copy)")
        void defensivelyCopied() {
            List<SubscriptionAttempt> mutable = new ArrayList<>();
            mutable.add(anyAttempt(Instant.parse("2026-01-01T00:00:00Z")));

            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, mutable);

            mutable.clear();

            assertThat(history.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("attempts() 가 반환한 리스트는 수정 불가")
        void returnedListImmutable() {
            SubscriptionHistory history = SubscriptionHistory.of(MEMBER, List.of(
                anyAttempt(Instant.parse("2026-01-01T00:00:00Z"))
            ));

            assertThatThrownBy(() -> history.attempts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
