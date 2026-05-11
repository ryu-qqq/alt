package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.manager.HistorySummaryReadManager;
import com.ryuqqq.alt.application.subscription.manager.SubscriptionAttemptReadManager;
import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.error.MemberNotFoundException;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력 조회 readOnly 트랜잭션 묶음.
 *
 * Member + SubscriptionHistory + 사용된 Channel + 영속 Summary 까지 하나의 readOnly 트랜잭션에서 조회한다.
 * 각 ReadManager 의 readOnly 트랜잭션은 Spring 의 PROPAGATION_REQUIRED 기본 정책에 따라
 * 본 Facade 의 트랜잭션에 참여한다.
 *
 * 영속 Summary 는 Bundle 안에서 fingerprint 비교에 사용되며, 일치 시 LLM 호출이 스킵된다.
 */
@Component
public class SubscriptionHistoryReadFacade {

    private final MemberQueryPort memberQueryPort;
    private final SubscriptionAttemptReadManager subscriptionAttemptReadManager;
    private final ChannelReadManager channelReadManager;
    private final HistorySummaryReadManager historySummaryReadManager;

    public SubscriptionHistoryReadFacade(
        MemberQueryPort memberQueryPort,
        SubscriptionAttemptReadManager subscriptionAttemptReadManager,
        ChannelReadManager channelReadManager,
        HistorySummaryReadManager historySummaryReadManager
    ) {
        this.memberQueryPort = memberQueryPort;
        this.subscriptionAttemptReadManager = subscriptionAttemptReadManager;
        this.channelReadManager = channelReadManager;
        this.historySummaryReadManager = historySummaryReadManager;
    }

    @Transactional(readOnly = true)
    public SubscriptionHistoryReadBundle findByPhoneNumber(PhoneNumber phoneNumber) {
        Member member = memberQueryPort.findByPhoneNumber(phoneNumber)
            .orElseThrow(() -> new MemberNotFoundException("phoneNumber=" + phoneNumber.value()));

        SubscriptionHistory history = subscriptionAttemptReadManager.findHistoryByMemberId(member.id());
        Channels channels = channelReadManager.findByIds(history.committedChannelIds());
        HistorySummary persistedSummary = historySummaryReadManager.find(member.id()).orElse(null);

        return SubscriptionHistoryReadBundle.of(member, history, channels, persistedSummary);
    }
}
