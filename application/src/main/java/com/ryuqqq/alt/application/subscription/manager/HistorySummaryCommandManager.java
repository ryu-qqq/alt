package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryCommandPort;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistorySummaryCommandManager {

    private final HistorySummaryCommandPort historySummaryCommandPort;

    public HistorySummaryCommandManager(HistorySummaryCommandPort historySummaryCommandPort) {
        this.historySummaryCommandPort = historySummaryCommandPort;
    }

    @Transactional
    public void persist(HistorySummary summary) {
        historySummaryCommandPort.persist(summary);
    }
}
