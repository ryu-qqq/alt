package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원의 구독 시도 이력을 감싸는 컬렉션 VO.
 *
 * 설계 근거:
 * - List<SubscriptionAttempt> 를 그대로 노출하지 않고 1급 도메인 객체로 승격해
 *   호출자(컨트롤러, LLM 어댑터)가 동일한 도메인 어휘를 사용하도록 한다.
 * - 정렬·필터링은 영속 어댑터(persistence-mysql)의 ORDER BY/WHERE 가 담당한다.
 *   COMMITTED 만 노출하는 정책은 사용자 화면/LLM 요약 양쪽이 공유하므로 컬렉션 VO 가
 *   1급 메서드(committedAttempts / committedChannelIds / hasCommitted) 로 표현한다.
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

    /**
     * COMMITTED 시도만 추출. 어댑터 정렬을 보존.
     */
    public List<SubscriptionAttempt> committedAttempts() {
        return attempts.stream()
            .filter(SubscriptionAttempt::isCommitted)
            .toList();
    }

    /**
     * COMMITTED 시도가 사용한 채널 식별자 집합 (중복 제거).
     */
    public Set<ChannelId> committedChannelIds() {
        return attempts.stream()
            .filter(SubscriptionAttempt::isCommitted)
            .map(SubscriptionAttempt::channelId)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean hasCommitted() {
        return attempts.stream().anyMatch(SubscriptionAttempt::isCommitted);
    }

    /**
     * 가장 최신(가장 큰 attemptId) COMMITTED 시도의 ID 값. COMMITTED 가 없으면 0L.
     * 호출자(캐시/요약 무효화 등) 가 이력의 변경 여부를 판단하는 식별자로 사용한다.
     */
    public long latestCommittedAttemptId() {
        return attempts.stream()
            .filter(SubscriptionAttempt::isCommitted)
            .mapToLong(SubscriptionAttempt::idValue)
            .max()
            .orElse(0L);
    }
}
