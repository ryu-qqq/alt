package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.HistorySummaryEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.HistorySummaryJpaRepository;
import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HistorySummaryQueryAdapter implements HistorySummaryQueryPort {

    private final HistorySummaryJpaRepository historySummaryJpaRepository;
    private final HistorySummaryEntityMapper historySummaryEntityMapper;

    public HistorySummaryQueryAdapter(
        HistorySummaryJpaRepository historySummaryJpaRepository,
        HistorySummaryEntityMapper historySummaryEntityMapper
    ) {
        this.historySummaryJpaRepository = historySummaryJpaRepository;
        this.historySummaryEntityMapper = historySummaryEntityMapper;
    }

    @Override
    public Optional<HistorySummary> find(MemberId memberId) {
        return historySummaryJpaRepository.findById(memberId.value())
            .map(historySummaryEntityMapper::toDomain);
    }
}
