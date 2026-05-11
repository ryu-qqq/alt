package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.HistorySummaryEntityMapper;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.HistorySummaryJpaRepository;
import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryCommandPort;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.springframework.stereotype.Component;

/**
 * memberId 가 PK 라 JpaRepository.save() 가 upsert 로 동작한다.
 * 같은 회원에 대한 LLM 요약은 항상 단일 레코드만 유지 — 재호출 시 fingerprint/summary 갱신.
 */
@Component
public class HistorySummaryCommandAdapter implements HistorySummaryCommandPort {

    private final HistorySummaryJpaRepository historySummaryJpaRepository;
    private final HistorySummaryEntityMapper historySummaryEntityMapper;

    public HistorySummaryCommandAdapter(
        HistorySummaryJpaRepository historySummaryJpaRepository,
        HistorySummaryEntityMapper historySummaryEntityMapper
    ) {
        this.historySummaryJpaRepository = historySummaryJpaRepository;
        this.historySummaryEntityMapper = historySummaryEntityMapper;
    }

    @Override
    public void persist(HistorySummary summary) {
        historySummaryJpaRepository.save(historySummaryEntityMapper.toEntity(summary));
    }
}
