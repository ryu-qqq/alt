package com.ryuqqq.alt.application.subscription.dto;

import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;

import java.util.List;

/**
 * 이력 조회 readOnly 트랜잭션 묶음.
 *
 * SubscriptionHistoryReadFacade 가 Member + SubscriptionHistory + Channels + 영속 HistorySummary 까지
 * 한 트랜잭션에 조회 후 of(...) 로 빌드한다. 빌드 시점에 committed 추출과 fingerprint 계산을
 * 모두 끝내 호출자(Service) 가 stream 을 다시 돌리지 않도록 한다.
 *
 * 노출 헬퍼:
 * - memberId / phoneNumber  : LoD 위반(2단계 체이닝) 방지를 위한 1단계 accessor
 * - hasCommitted            : LLM 호출 가드
 * - committedAttempts       : Assembler 가 view 변환 시 활용
 * - fingerprint             : 캐시 무효화 키 (최신 committed attemptId, 빈 경우 0L)
 * - hasMatchingSummary      : 영속 summary 의 fingerprint 가 현재와 일치하는지 — true 면 LLM 스킵
 * - persistedSummary        : 일치 시 그대로 사용할 영속 도메인 객체 (없으면 null)
 */
public record SubscriptionHistoryReadBundle(
    Member member,
    SubscriptionHistory history,
    List<SubscriptionAttempt> committedAttempts,
    Channels channels,
    long fingerprint,
    HistorySummary persistedSummary
) {

    public SubscriptionHistoryReadBundle {
        committedAttempts = List.copyOf(committedAttempts);
    }

    public static SubscriptionHistoryReadBundle of(
        Member member,
        SubscriptionHistory history,
        Channels channels,
        HistorySummary persistedSummary
    ) {
        return new SubscriptionHistoryReadBundle(
            member,
            history,
            history.committedAttempts(),
            channels,
            history.latestCommittedAttemptId(),
            persistedSummary
        );
    }

    public MemberId memberId() {
        return member.id();
    }

    public PhoneNumber phoneNumber() {
        return member.phoneNumber();
    }

    public boolean hasCommitted() {
        return !committedAttempts.isEmpty();
    }

    /**
     * 영속 summary 가 현재 fingerprint 와 일치하면 LLM 호출 없이 그대로 사용한다.
     */
    public boolean hasMatchingSummary() {
        return persistedSummary != null && persistedSummary.fingerprint() == fingerprint;
    }
}
