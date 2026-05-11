package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;

import java.util.List;

/**
 * SubscriptionHistory 컬렉션 VO 테스트용 Fixture.
 *
 * - 도메인은 순수 자바이므로 정렬은 어댑터가 책임진다 (List.copyOf 입력 그대로 보존).
 * - 픽스쳐는 호출자(테스트)가 명시한 시나리오를 그대로 반영한다.
 */
public final class SubscriptionHistoryFixture {

    private SubscriptionHistoryFixture() {}

    public static final MemberId DEFAULT_MEMBER_ID = SubscriptionAttemptFixture.DEFAULT_MEMBER_ID;

    public static SubscriptionHistory empty() {
        return SubscriptionHistory.empty(DEFAULT_MEMBER_ID);
    }

    public static SubscriptionHistory singleCommitted() {
        return SubscriptionHistory.of(
            DEFAULT_MEMBER_ID,
            List.of(SubscriptionAttemptFixture.reconstitutedCommitted(1L))
        );
    }

    public static SubscriptionHistory mixedStatuses() {
        return SubscriptionHistory.of(
            DEFAULT_MEMBER_ID,
            List.of(
                SubscriptionAttemptFixture.reconstitutedCommitted(1L, ChannelId.of(11L)),
                SubscriptionAttemptFixture.reconstitutedRolledBack(2L),
                SubscriptionAttemptFixture.reconstitutedFailed(3L, AttemptFailureReason.EXTERNAL_TIMEOUT),
                SubscriptionAttemptFixture.reconstitutedCommitted(4L, ChannelId.of(12L))
            )
        );
    }

    public static SubscriptionHistory onlyNonCommitted() {
        return SubscriptionHistory.of(
            DEFAULT_MEMBER_ID,
            List.of(
                SubscriptionAttemptFixture.reconstitutedRolledBack(1L),
                SubscriptionAttemptFixture.reconstitutedFailed(2L, AttemptFailureReason.EXTERNAL_SERVER_ERROR)
            )
        );
    }

    public static SubscriptionHistory of(MemberId memberId, List<SubscriptionAttempt> attempts) {
        return SubscriptionHistory.of(memberId, attempts);
    }
}
