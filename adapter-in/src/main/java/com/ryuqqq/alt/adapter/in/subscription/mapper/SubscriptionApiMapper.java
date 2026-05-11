package com.ryuqqq.alt.adapter.in.subscription.mapper;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.QuerySubscriptionHistoryApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.UnsubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.QuerySubscriptionHistoryApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.SubscribeApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.SubscriptionHistoryItemApiView;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.UnsubscribeApiResponse;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.PhoneNumber;

/**
 * Request DTO ↔ Application Command/Result 변환.
 * static 유틸 (인스턴스화 금지). VO 변환은 Adapter-in 의 책임.
 */
public final class SubscriptionApiMapper {

    private SubscriptionApiMapper() {
    }

    public static SubscribeCommand toSubscribeCommand(SubscribeApiRequest request, String idempotencyKey) {
        return new SubscribeCommand(
            PhoneNumber.of(request.phoneNumber()),
            ChannelId.of(request.channelId()),
            request.targetStatus(),
            idempotencyKey
        );
    }

    public static SubscribeApiResponse toSubscribeResponse(SubscribeResult result) {
        return new SubscribeApiResponse(
            result.attemptId(),
            result.status() != null ? result.status().name() : null,
            result.currentStatus().name(),
            result.failureReason()
        );
    }

    public static UnsubscribeCommand toUnsubscribeCommand(UnsubscribeApiRequest request, String idempotencyKey) {
        return new UnsubscribeCommand(
            PhoneNumber.of(request.phoneNumber()),
            ChannelId.of(request.channelId()),
            request.targetStatus(),
            idempotencyKey
        );
    }

    public static UnsubscribeApiResponse toUnsubscribeResponse(UnsubscribeResult result) {
        return new UnsubscribeApiResponse(
            result.attemptId(),
            result.status() != null ? result.status().name() : null,
            result.currentStatus().name(),
            result.failureReason()
        );
    }

    public static QuerySubscriptionHistoryQuery toQuerySubscriptionHistoryQuery(QuerySubscriptionHistoryApiRequest request) {
        return QuerySubscriptionHistoryQuery.of(PhoneNumber.of(request.phoneNumber()));
    }

    public static QuerySubscriptionHistoryApiResponse toHistoryResponse(QuerySubscriptionHistoryResult result) {
        return new QuerySubscriptionHistoryApiResponse(
            result.history().stream()
                .map(SubscriptionApiMapper::toHistoryItemView)
                .toList(),
            result.summary(),
            result.summaryGeneratedAt(),
            result.summaryStale()
        );
    }

    private static SubscriptionHistoryItemApiView toHistoryItemView(SubscriptionHistoryItemView item) {
        return new SubscriptionHistoryItemApiView(
            item.attemptId(),
            item.channelId(),
            item.channelName(),
            item.kind().name(),
            item.fromStatus().name(),
            item.toStatus().name(),
            item.occurredAt()
        );
    }
}
