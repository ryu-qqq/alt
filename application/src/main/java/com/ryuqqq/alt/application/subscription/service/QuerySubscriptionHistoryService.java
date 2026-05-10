package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.dto.llm.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.llm.SubscriptionHistorySummaryRequest;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.application.subscription.manager.ChannelReadManager;
import com.ryuqqq.alt.application.subscription.manager.LlmSummaryClientManager;
import com.ryuqqq.alt.application.subscription.manager.MemberReadManager;
import com.ryuqqq.alt.application.subscription.manager.SubscriptionAttemptReadManager;
import com.ryuqqq.alt.application.subscription.port.in.QuerySubscriptionHistoryUseCase;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이력 조회 + LLM 자연어 요약. LLM 실패 시 graceful degradation (이력만 반환, summary=null).
 *
 * 표시되는 이력은 COMMITTED 시도만 포함한다 (운영용 ROLLED_BACK / FAILED 는 사용자 노출 X).
 */
@Service
public class QuerySubscriptionHistoryService implements QuerySubscriptionHistoryUseCase {

    private final MemberReadManager memberReadManager;
    private final ChannelReadManager channelReadManager;
    private final SubscriptionAttemptReadManager subscriptionAttemptReadManager;
    private final LlmSummaryClientManager llmSummaryClientManager;

    public QuerySubscriptionHistoryService(
        MemberReadManager memberReadManager,
        ChannelReadManager channelReadManager,
        SubscriptionAttemptReadManager subscriptionAttemptReadManager,
        LlmSummaryClientManager llmSummaryClientManager
    ) {
        this.memberReadManager = memberReadManager;
        this.channelReadManager = channelReadManager;
        this.subscriptionAttemptReadManager = subscriptionAttemptReadManager;
        this.llmSummaryClientManager = llmSummaryClientManager;
    }

    @Override
    public QuerySubscriptionHistoryResult execute(QuerySubscriptionHistoryQuery query) {
        Member member = memberReadManager.getByPhoneNumber(query.phoneNumber());
        SubscriptionHistory history = subscriptionAttemptReadManager.findHistoryByMemberId(member.id());

        List<SubscriptionAttempt> committed = history.attempts().stream()
            .filter(SubscriptionAttempt::isCommitted)
            .toList();

        Map<Long, Channel> channels = resolveChannels(committed);

        List<SubscriptionHistoryItemView> items = committed.stream()
            .map(a -> toItemView(a, channels.get(a.channelId().value())))
            .toList();

        if (items.isEmpty()) {
            return QuerySubscriptionHistoryResult.withoutSummary(items);
        }

        SubscriptionHistorySummaryRequest llmRequest = new SubscriptionHistorySummaryRequest(
            member.phoneNumber().value(),
            items.stream()
                .map(i -> new SubscriptionHistorySummaryRequest.HistoryItem(
                    i.channelName(),
                    i.fromStatus().displayName(),
                    i.toStatus().displayName(),
                    i.kind().displayName(),
                    i.occurredAt()
                ))
                .toList()
        );

        LlmSummaryOutcome outcome = llmSummaryClientManager.summarize(llmRequest);
        return switch (outcome) {
            case LlmSummaryOutcome.Success success ->
                QuerySubscriptionHistoryResult.of(items, success.summary());
            case LlmSummaryOutcome.Unavailable ignored ->
                QuerySubscriptionHistoryResult.withoutSummary(items);
        };
    }

    private Map<Long, Channel> resolveChannels(List<SubscriptionAttempt> attempts) {
        Map<Long, Channel> channels = new HashMap<>();
        for (SubscriptionAttempt attempt : attempts) {
            Long key = attempt.channelId().value();
            if (!channels.containsKey(key)) {
                channels.put(key, channelReadManager.getById(ChannelId.of(key)));
            }
        }
        return channels;
    }

    private SubscriptionHistoryItemView toItemView(SubscriptionAttempt attempt, Channel channel) {
        return new SubscriptionHistoryItemView(
            attempt.id().value(),
            channel.idValue(),
            channel.name(),
            attempt.kind(),
            attempt.fromStatus(),
            attempt.toStatus(),
            attempt.completedAt()
        );
    }
}
