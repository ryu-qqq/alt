package com.ryuqqq.alt.application.subscription.port.in;

import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;

/**
 * 구독 이력 조회 UseCase (Port-In).
 * 응답에는 이력 목록과 LLM 자연어 요약이 포함된다. LLM 실패 시 graceful degradation.
 */
public interface QuerySubscriptionHistoryUseCase {

    QuerySubscriptionHistoryResult execute(QuerySubscriptionHistoryQuery query);
}
