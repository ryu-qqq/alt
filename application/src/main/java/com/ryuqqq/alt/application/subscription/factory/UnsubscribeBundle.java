package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.policy.SubscriptionTransitionPolicy;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

/**
 * 한 번의 해지 시도에 필요한 도메인 컨텍스트 묶음 (Member + Channel + SubscriptionAttempt).
 *
 * 모든 saga 단계 / 검증 / 결과 산출은 번들 자신의 메서드로 노출해서 호출자가
 * bundle.member().status() 같은 2단계 체이닝을 하지 않도록 한다.
 *
 * 라이프사이클:
 *   1) UnsubscribeFactory.createBundle(member, command) — 초기 빌드 (영속 member, channel=null,
 *      attempt=COMMITTED with fromStatus=member.status, toStatus=command.targetStatus)
 *   2) withChannel(fetched) — channel 주입
 *   3) verifyTransition() — 도메인 정책 검증 (채널 해지 권한 + 해지 상태 전이)
 *   4) 코디네이터: applyApproved / applyRejected / applyFailed 로 saga 결과 적용
 *   5) toResult() — 최종 응답 DTO 추출
 */
public record UnsubscribeBundle(
    Member member,
    Channel channel,
    SubscriptionAttempt attempt
) {

    public UnsubscribeBundle withChannel(Channel resolvedChannel) {
        return new UnsubscribeBundle(member, resolvedChannel, attempt);
    }

    /** 도메인 정책 검증 (채널 해지 권한 + 해지 상태 전이) */
    public void verifyTransition() {
        SubscriptionTransitionPolicy.verifyUnsubscribe(member, channel, attempt.toStatus());
    }

    /** APPROVED: member 상태 적용. attempt 는 COMMITTED 그대로 유지. 같은 번들 반환 */
    public UnsubscribeBundle applyApproved() {
        member.applyUnsubscribe(attempt.toStatus());
        return this;
    }

    /** REJECTED: attempt 만 ROLLED_BACK 으로 변환된 새 번들 반환. member 변경 X */
    public UnsubscribeBundle applyRejected() {
        return new UnsubscribeBundle(member, channel, attempt.asRolledBack());
    }

    /** 호출 실패: attempt 만 FAILED 로 변환된 새 번들 반환. member 변경 X */
    public UnsubscribeBundle applyFailed(AttemptFailureReason reason, String detail) {
        return new UnsubscribeBundle(member, channel, attempt.asFailed(reason, detail));
    }

    /** 영속화 직후 DB 채번 attemptId 가 반영된 attempt 로 갈아끼운 새 번들 반환. */
    public UnsubscribeBundle withPersistedAttempt(SubscriptionAttempt persistedAttempt) {
        return new UnsubscribeBundle(member, channel, persistedAttempt);
    }

    public UnsubscribeResult toResult() {
        return UnsubscribeResult.from(attempt, member.status());
    }
}
