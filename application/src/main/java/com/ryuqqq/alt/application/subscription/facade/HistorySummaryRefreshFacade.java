package com.ryuqqq.alt.application.subscription.facade;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.manager.HistorySummaryCommandManager;
import com.ryuqqq.alt.application.subscription.manager.LlmSummaryClientManager;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;

/**
 * 이력 요약 갱신 사가.
 *
 * LLM 호출과 결과 영속화를 한 책임으로 묶어 Service 가 외부 호출 결과를 분기/저장하지 않게 한다.
 * - 정상  : DB persist 후 결과 반환
 * - 실패  : persist 스킵 + 결과 그대로 반환 (호출자가 graceful 처리, 다음 호출 재시도 기회 보존)
 *
 * @Transactional 없음 — LLM 호출이 DB 트랜잭션에 묶이면 안 되며, CommandManager 가 자체 트랜잭션을 갖는다.
 */
@Component
public class HistorySummaryRefreshFacade {

    private final LlmSummaryClientManager llmSummaryClientManager;
    private final HistorySummaryCommandManager historySummaryCommandManager;

    public HistorySummaryRefreshFacade(
        LlmSummaryClientManager llmSummaryClientManager,
        HistorySummaryCommandManager historySummaryCommandManager
    ) {
        this.llmSummaryClientManager = llmSummaryClientManager;
        this.historySummaryCommandManager = historySummaryCommandManager;
    }

    public LlmSummaryOutcome refresh(SubscriptionHistoryReadBundle bundle) {
        LlmSummaryOutcome outcome = llmSummaryClientManager.summarize(bundle);
        if (outcome.isAvailable()) {
            historySummaryCommandManager.persist(
                HistorySummary.of(bundle.memberId(), bundle.fingerprint(), outcome.summary())
            );
        }
        return outcome;
    }
}
