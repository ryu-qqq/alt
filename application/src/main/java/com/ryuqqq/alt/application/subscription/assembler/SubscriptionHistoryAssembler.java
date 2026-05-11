package com.ryuqqq.alt.application.subscription.assembler;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.domain.channel.Channels;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 이력 조회 결과 조립 Assembler.
 *
 * Bundle + LlmSummaryOutcome → 응답 DTO 변환만 책임진다.
 * (LLM 입력 변환은 LlmClientAdapter 의 매퍼가 담당.)
 *
 * 노출 정책: view 는 어댑터 정렬(최신순 DESC) 보존. summary 는 outcome 의 nullable 값 그대로 노출.
 */
@Component
public class SubscriptionHistoryAssembler {

    public QuerySubscriptionHistoryResult toResult(SubscriptionHistoryReadBundle bundle, LlmSummaryOutcome outcome) {
        Channels channels = bundle.channels();
        List<SubscriptionHistoryItemView> views = bundle.committedAttempts().stream()
            .map(attempt -> new SubscriptionHistoryItemView(
                attempt.idValue(),
                attempt.channelIdValue(),
                channels.nameOf(attempt.channelId()),
                attempt.kind(),
                attempt.fromStatus(),
                attempt.toStatus(),
                attempt.completedAt()
            ))
            .toList();
        return QuerySubscriptionHistoryResult.of(views, outcome);
    }
}
