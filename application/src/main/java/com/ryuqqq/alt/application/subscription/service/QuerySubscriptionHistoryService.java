package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.assembler.SubscriptionHistoryAssembler;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.facade.HistorySummaryRefreshFacade;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionHistoryReadFacade;
import com.ryuqqq.alt.application.subscription.port.in.QuerySubscriptionHistoryUseCase;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Service;

/**
 * 구독 이력 조회 UseCase.
 *
 * 조회 흐름 (모든 분기가 assembler.toResult 를 단일 출구로 통과):
 *   1. ReadFacade: Member + Attempts + Channels + 영속 Summary 까지 readOnly 트랜잭션 한 번에 조회
 *      → Bundle 빌드 시점에 committed/fingerprint 까지 미리 계산
 *   2. resolveSummary 가 LLM outcome 을 결정한다
 *      - COMMITTED 0건 → empty() (호출 불필요)
 *      - 영속 Summary fingerprint 일치 → success(persisted) (LLM 스킵, fresh)
 *      - fingerprint 불일치 → RefreshFacade 가 LLM 호출
 *          · 성공 → success (fresh, DB 갱신)
 *          · 실패 + 영속체 있음 → staleSuccess (옛 요약 + stale=true, DB 미갱신)
 *          · 실패 + 영속체 없음 → unavailable (summary=null)
 *   3. assembler.toResult(bundle, outcome) 로 응답 조립 일원화
 *
 * 정책:
 * - 같은 이력 상태에 대한 중복 LLM 호출 회피 (비용/지연)
 * - LLM 실패 시 폴백 우선순위: 영속체 옛 요약(stale=true) > null
 *   → 클라이언트는 stale 여부와 generatedAt 으로 신선도 판단 가능
 * - 영속체 미갱신 → 다음 호출에 재시도 기회 보존
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
            HistorySummary persisted = bundle.persistedSummary();
            return LlmSummaryOutcome.success(persisted.summary(), persisted.generatedAt());
        }
        LlmSummaryOutcome refreshed = historySummaryRefreshFacade.refresh(bundle);
        if (refreshed.isAvailable()) {
            return refreshed;
        }
        // LLM 실패 — 영속체 폴백 (있으면 stale 로 표시, 없으면 unavailable 그대로)
        HistorySummary fallback = bundle.persistedSummary();
        if (fallback != null) {
            return LlmSummaryOutcome.staleSuccess(fallback.summary(), fallback.generatedAt());
        }
        return refreshed;
    }
}
