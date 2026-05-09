package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 회원의 구독 시도 이력을 감싸는 컬렉션 VO.
 *
 * 설계 근거 (research-2026-05):
 * - Stripe / Recurly / Paddle 모두 이벤트 로그(append-only)를 SoT 로 사용한다.
 * - "현재 상태"는 캐시(Member.status)에 두지만, 이력 자체에서도 derive 가능해야 한다 (불변식 검증용).
 * - List<SubscriptionAttempt>를 그대로 노출하면 호출자가 매번 filter/sort 해야 하므로 응집도 손실.
 *   → 컬렉션을 1급 도메인 객체로 승격하여 의도 있는 메서드를 노출한다.
 *
 * 불변성:
 * - 내부 attempts 는 List.copyOf 로 방어 복사 + requestedAt 오름차순 정렬.
 * - record equality 는 memberId + attempts 동일성 기반 (자동 생성).
 */
public record SubscriptionHistory(
    MemberId memberId,
    List<SubscriptionAttempt> attempts
) {

    public SubscriptionHistory {
        attempts = List.copyOf(
            attempts.stream()
                .sorted(Comparator.comparing(SubscriptionAttempt::requestedAt))
                .toList()
        );
    }

    public static SubscriptionHistory of(MemberId memberId, List<SubscriptionAttempt> attempts) {
        return new SubscriptionHistory(memberId, attempts);
    }

    public static SubscriptionHistory empty(MemberId memberId) {
        return new SubscriptionHistory(memberId, List.of());
    }

    /**
     * 사용자에게 노출되는 이력 — 성공적으로 커밋된 변경만 포함.
     */
    public List<SubscriptionAttempt> committedChanges() {
        return attempts.stream()
            .filter(SubscriptionAttempt::isCommitted)
            .toList();
    }

    /**
     * 가장 최근 커밋된 변경. 한 번도 변경된 적 없으면 empty.
     */
    public Optional<SubscriptionAttempt> latestCommittedChange() {
        List<SubscriptionAttempt> committed = committedChanges();
        return committed.isEmpty() ? Optional.empty() : Optional.of(committed.get(committed.size() - 1));
    }

    /**
     * 현재 구독 상태. Member.status 캐시와 일치해야 함을 호출처에서 불변식으로 검증할 수 있다.
     * 한 번도 커밋된 변경이 없으면 NONE 으로 간주 (가입 안 한 상태).
     */
    public SubscriptionStatus currentStatus() {
        return latestCommittedChange()
            .map(SubscriptionAttempt::toStatus)
            .orElse(SubscriptionStatus.NONE);
    }

    /**
     * 특정 시점 이후의 이력 슬라이스. LLM 요약 시 윈도우 제한에 사용.
     */
    public SubscriptionHistory since(Instant from) {
        List<SubscriptionAttempt> filtered = attempts.stream()
            .filter(a -> !a.requestedAt().isBefore(from))
            .toList();
        return new SubscriptionHistory(memberId, filtered);
    }

    public boolean isEmpty() {
        return attempts.isEmpty();
    }

    public int size() {
        return attempts.size();
    }
}
