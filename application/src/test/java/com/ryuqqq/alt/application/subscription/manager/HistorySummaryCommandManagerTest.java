package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryCommandPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistorySummaryCommandManager — 요약 영속 매니저 단위 테스트")
class HistorySummaryCommandManagerTest {

    @Mock HistorySummaryCommandPort historySummaryCommandPort;

    @InjectMocks HistorySummaryCommandManager manager;

    @Test
    @DisplayName("persist 호출은 port.persist 로 1회 위임된다")
    void shouldDelegatePersist() {
        // given
        HistorySummary summary = HistorySummary.of(MemberId.of(1L), 99L, "한 줄 요약");

        // when
        manager.persist(summary);

        // then
        verify(historySummaryCommandPort, times(1)).persist(summary);
    }
}
