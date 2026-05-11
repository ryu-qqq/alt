package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.SubscriptionAttemptQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionAttemptReadManager — 시도 조회 매니저 단위 테스트")
class SubscriptionAttemptReadManagerTest {

    @Mock SubscriptionAttemptQueryPort subscriptionAttemptQueryPort;

    @InjectMocks SubscriptionAttemptReadManager manager;

    @Nested
    @DisplayName("findByIdempotencyKey")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("null 키는 port 호출 없이 Optional.empty() 를 즉시 반환한다")
        void shouldReturnEmptyWithoutCallingPortWhenKeyNull() {
            // when
            Optional<SubscriptionAttempt> result = manager.findByIdempotencyKey(null);

            // then
            assertThat(result).isEmpty();
            verifyNoInteractions(subscriptionAttemptQueryPort);
        }

        @Test
        @DisplayName("정상 키는 port 결과를 그대로 반환한다 — 발견된 경우")
        void shouldReturnPresentWhenFound() {
            // given
            String key = "idem-001";
            SubscriptionAttempt found = SubscriptionAttemptFixture.reconstitutedCommitted(7L);
            given(subscriptionAttemptQueryPort.findByIdempotencyKey(key)).willReturn(Optional.of(found));

            // when
            Optional<SubscriptionAttempt> result = manager.findByIdempotencyKey(key);

            // then
            assertThat(result).contains(found);
        }

        @Test
        @DisplayName("정상 키는 port 결과를 그대로 반환한다 — 미발견 (empty)")
        void shouldReturnEmptyWhenNotFound() {
            // given
            String key = "idem-002";
            given(subscriptionAttemptQueryPort.findByIdempotencyKey(key)).willReturn(Optional.empty());

            // when
            Optional<SubscriptionAttempt> result = manager.findByIdempotencyKey(key);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findHistoryByMemberId")
    class FindHistoryByMemberId {

        @Test
        @DisplayName("port 가 attempts 리스트를 반환하면 SubscriptionHistory.of 로 감싼 결과를 반환한다")
        void shouldWrapAttemptsInSubscriptionHistory() {
            // given
            MemberId memberId = MemberId.of(1L);
            List<SubscriptionAttempt> attempts = List.of(
                SubscriptionAttemptFixture.reconstitutedCommitted(1L),
                SubscriptionAttemptFixture.reconstitutedRolledBack(2L)
            );
            given(subscriptionAttemptQueryPort.findAllByMemberId(memberId)).willReturn(attempts);

            // when
            SubscriptionHistory history = manager.findHistoryByMemberId(memberId);

            // then
            assertThat(history.memberId()).isEqualTo(memberId);
            assertThat(history.size()).isEqualTo(2);
            assertThat(history.attempts()).containsExactlyElementsOf(attempts);
        }

        @Test
        @DisplayName("port 가 빈 리스트를 반환하면 빈 SubscriptionHistory 를 반환한다")
        void shouldReturnEmptyHistoryWhenPortEmpty() {
            // given
            MemberId memberId = MemberId.of(2L);
            given(subscriptionAttemptQueryPort.findAllByMemberId(memberId)).willReturn(List.of());

            // when
            SubscriptionHistory history = manager.findHistoryByMemberId(memberId);

            // then
            assertThat(history.isEmpty()).isTrue();
            assertThat(history.memberId()).isEqualTo(memberId);
        }
    }
}
