package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.member.MemberId;

import java.util.List;

/**
 * 회원의 구독 시도 이력을 감싸는 컬렉션 VO.
 *
 * 설계 근거:
 * - List<SubscriptionAttempt> 를 그대로 노출하지 않고 1급 도메인 객체로 승격해
 *   호출자(컨트롤러, LLM 어댑터)가 동일한 도메인 어휘를 사용하도록 한다.
 * - 정렬·필터링은 영속 어댑터(persistence-mysql)의 ORDER BY/WHERE 가 담당한다.
 *   필요한 메서드(currentStatus, committedChanges, since 등)는 호출처가 생길 때 추가한다 (YAGNI).
 *
 * 불변성:
 * - 내부 attempts 는 List.copyOf 로 방어 복사. 정렬은 입력 그대로 보존.
 */
public record SubscriptionHistory(
    MemberId memberId,
    List<SubscriptionAttempt> attempts
) {

    public SubscriptionHistory {
        attempts = List.copyOf(attempts);
    }

    public static SubscriptionHistory of(MemberId memberId, List<SubscriptionAttempt> attempts) {
        return new SubscriptionHistory(memberId, attempts);
    }

    public static SubscriptionHistory empty(MemberId memberId) {
        return new SubscriptionHistory(memberId, List.of());
    }

    public boolean isEmpty() {
        return attempts.isEmpty();
    }

    public int size() {
        return attempts.size();
    }
}
