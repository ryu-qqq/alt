package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.assembler.SubscriptionHistoryAssembler;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.facade.HistorySummaryRefreshFacade;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionHistoryReadFacade;
import com.ryuqqq.alt.application.subscription.port.in.QuerySubscriptionHistoryUseCase;
import org.springframework.stereotype.Service;

/**
 * 구독 이력 조회 UseCase.
 *
 * 조회 흐름 (모든 분기가 assembler.toResult 를 단일 출구로 통과):
 *   1. ReadFacade: Member + Attempts + Channels + 영속 Summary 까지 readOnly 트랜잭션 한 번에 조회
 *      → Bundle 빌드 시점에 committed/fingerprint 까지 미리 계산
 *   2. resolveSummary 가 LLM outcome 을 결정한다
 *      - COMMITTED 0건 → LlmSummaryOutcome.empty() (호출 불필요)
 *      - 영속 Summary fingerprint 일치 → LlmSummaryOutcome.success(persisted) (LLM 스킵)
 *      - 없거나 stale → RefreshFacade 가 LLM 호출 + 성공 시 DB persist 까지 묶음 처리
 *   3. assembler.toResult(bundle, outcome) 로 응답 조립 일원화
 *
 * 정책:
 * - 같은 이력 상태에 대한 중복 LLM 호출 회피 (비용/지연)
 * - LLM Unavailable 응답은 summary=null 로 graceful degradation, 저장 X (다음 호출 재시도)
 * - DB 가 단일 source-of-truth (별도 캐시 없음)
 */
@Service
public class QuerySubscriptionHistoryService implements QuerySubscriptionHistoryUseCase {

    private final SubscriptionHistoryReadFacade subscriptionHistoryReadFacade;
    private final HistorySummaryRefreshFacade historySummaryRefreshFacade;
    private final SubscriptionHistoryAssembler assembler;

    public QuerySubscriptionHistoryService(
        SubscriptionHistoryReadFacade subscriptionHistoryReadFacade,
        HistorySummaryRefreshFacade historySummaryRefreshFacade,
        SubscriptionHistoryAssembler assembler
    ) {
        this.subscriptionHistoryReadFacade = subscriptionHistoryReadFacade;
        this.historySummaryRefreshFacade = historySummaryRefreshFacade;
        this.assembler = assembler;
    }

    @Override
    public QuerySubscriptionHistoryResult execute(QuerySubscriptionHistoryQuery query) {
        SubscriptionHistoryReadBundle bundle = subscriptionHistoryReadFacade.findByPhoneNumber(query.phoneNumber());
        return assembler.toResult(bundle, resolveSummary(bundle));
    }

    private LlmSummaryOutcome resolveSummary(SubscriptionHistoryReadBundle bundle) {
        if (!bundle.hasCommitted()) {
            return LlmSummaryOutcome.empty();
        }
        if (bundle.hasMatchingSummary()) {
            return LlmSummaryOutcome.success(bundle.persistedSummary().summary());
        }
        return historySummaryRefreshFacade.refresh(bundle);
    }
}
