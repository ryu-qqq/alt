package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.port.out.RandomClient;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RandomClientManager — 외부 random 호출 래퍼 단위 테스트")
class RandomClientManagerTest {

    @Mock RandomClient randomClient;

    @InjectMocks RandomClientManager manager;

    @Test
    @DisplayName("APPROVED 응답을 그대로 위임하여 반환한다")
    void shouldDelegateApproved() {
        // given
        given(randomClient.call()).willReturn(ExternalCallResult.APPROVED);

        // when
        ExternalCallResult result = manager.call();

        // then
        assertThat(result).isEqualTo(ExternalCallResult.APPROVED);
        verify(randomClient, times(1)).call();
    }

    @Test
    @DisplayName("REJECTED 응답을 그대로 위임하여 반환한다")
    void shouldDelegateRejected() {
        // given
        given(randomClient.call()).willReturn(ExternalCallResult.REJECTED);

        // when
        ExternalCallResult result = manager.call();

        // then
        assertThat(result).isEqualTo(ExternalCallResult.REJECTED);
        verify(randomClient, times(1)).call();
    }

    @Test
    @DisplayName("RandomClientException 은 변환 없이 그대로 propagate 된다")
    void shouldPropagateRandomClientException() {
        // given
        RandomClientException expected = new RandomClientException(AttemptFailureReason.EXTERNAL_TIMEOUT, "timed out");
        given(randomClient.call()).willThrow(expected);

        // when & then
        assertThatThrownBy(() -> manager.call())
            .isInstanceOf(RandomClientException.class)
            .isSameAs(expected);
    }
}
