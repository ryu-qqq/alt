package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.HistorySummaryQueryPort;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistorySummaryReadManager — 요약 조회 매니저 단위 테스트")
class HistorySummaryReadManagerTest {

    @Mock HistorySummaryQueryPort historySummaryQueryPort;

    @InjectMocks HistorySummaryReadManager manager;

    @Test
    @DisplayName("port 가 Summary 를 반환하면 그대로 위임 결과를 반환한다")
    void shouldReturnPresentWhenFound() {
        // given
        MemberId id = MemberId.of(1L);
        HistorySummary summary = HistorySummary.of(id, 1L, "캐시된 요약");
        given(historySummaryQueryPort.find(id)).willReturn(Optional.of(summary));

        // when
        Optional<HistorySummary> result = manager.find(id);

        // then
        assertThat(result).contains(summary);
    }

    @Test
    @DisplayName("port 가 Optional.empty 를 반환하면 그대로 빈 Optional 반환")
    void shouldReturnEmptyWhenNotFound() {
        // given
        MemberId id = MemberId.of(2L);
        given(historySummaryQueryPort.find(id)).willReturn(Optional.empty());

        // when
        Optional<HistorySummary> result = manager.find(id);

        // then
        assertThat(result).isEmpty();
    }
}
