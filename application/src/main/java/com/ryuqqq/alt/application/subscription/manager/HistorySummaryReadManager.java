package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class HistorySummaryReadManager {

    private final HistorySummaryQueryPort historySummaryQueryPort;

    public HistorySummaryReadManager(HistorySummaryQueryPort historySummaryQueryPort) {
        this.historySummaryQueryPort = historySummaryQueryPort;
    }

    @Transactional(readOnly = true)
    public Optional<HistorySummary> find(MemberId memberId) {
        return historySummaryQueryPort.find(memberId);
    }
}
