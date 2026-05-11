package com.ryuqqq.alt.application.subscription.factory;

import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.policy.SubscriptionTransitionPolicy;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;

/**
 * 한 번의 구독 시도에 필요한 도메인 컨텍스트 묶음 (Member + Channel + SubscriptionAttempt).
 *
 * 모든 saga 단계 / 검증 / 결과 산출은 번들 자신의 메서드로 노출해서 호출자가
 * bundle.member().status() 같은 2단계 체이닝을 하지 않도록 한다.
 *
 * 라이프사이클:
 *   1) SubscribeFactory.createBundle(command) — 초기 빌드 (member draft, channel=null, attempt=COMMITTED with toStatus=command.targetStatus)
 *   2) withMember(persistedMember) — DB 채번된 member 와 attempt.memberId 동기화
 *   3) (registration-only 통과 시) withChannel(fetched) — channel 주입
 *   4) verifyTransition() — 도메인 정책 검증
 *   5) 코디네이터: applyApproved / applyRejected / applyFailed 로 saga 결과 적용
 *   6) toResult() — 최종 응답 DTO 추출
 */
public record SubscribeBundle(
    Member member,
    Channel channel,
    SubscriptionAttempt attempt
) {

    public SubscribeBundle withMember(Member persistedMember) {
        return new SubscribeBundle(
            persistedMember, channel, attempt.withMemberId(persistedMember.id())
        );
    }

    public SubscribeBundle withChannel(Channel resolvedChannel) {
        return new SubscribeBundle(member, resolvedChannel, attempt);
    }

    public boolean isRegistrationOnly() {
        return member.status() == SubscriptionStatus.NONE
            && attempt.toStatus() == SubscriptionStatus.NONE;
    }

    public SubscriptionStatus memberStatus() {
        return member.status();
    }

    /** 도메인 정책 검증 (채널 권한 + 상태 전이) */
    public void verifyTransition() {
        SubscriptionTransitionPolicy.verifySubscribe(member, channel, attempt.toStatus());
    }

    /** APPROVED: member 상태 적용. attempt 는 COMMITTED 그대로 유지. 같은 번들 반환 */
    public SubscribeBundle applyApproved() {
        member.applySubscribe(attempt.toStatus());
        return this;
    }

    /** REJECTED: attempt 만 ROLLED_BACK 으로 변환된 새 번들 반환. member 변경 X */
    public SubscribeBundle applyRejected() {
        return new SubscribeBundle(member, channel, attempt.asRolledBack());
    }

    /** 호출 실패: attempt 만 FAILED 로 변환된 새 번들 반환. member 변경 X */
    public SubscribeBundle applyFailed(AttemptFailureReason reason, String detail) {
        return new SubscribeBundle(member, channel, attempt.asFailed(reason, detail));
    }

    /** 영속화 직후 DB 채번 attemptId 가 반영된 attempt 로 갈아끼운 새 번들 반환. */
    public SubscribeBundle withPersistedAttempt(SubscriptionAttempt persistedAttempt) {
        return new SubscribeBundle(member, channel, persistedAttempt);
    }

    public SubscribeResult toResult() {
        return SubscribeResult.from(attempt, member.status());
    }
}
