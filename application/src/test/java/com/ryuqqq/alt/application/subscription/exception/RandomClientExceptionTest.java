package com.ryuqqq.alt.application.subscription.exception;

import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RandomClientException — 외부 호출 실패 예외 단위 테스트")
class RandomClientExceptionTest {

    @Test
    @DisplayName("reason / detail 이 그대로 보존되고 메시지는 'REASON: detail' 포맷이다")
    void shouldPreserveReasonAndDetailWithFormattedMessage() {
        // given
        AttemptFailureReason reason = AttemptFailureReason.EXTERNAL_TIMEOUT;
        String detail = "HTTP timeout 2000ms";

        // when
        RandomClientException ex = new RandomClientException(reason, detail);

        // then
        assertThat(ex.reason()).isEqualTo(reason);
        assertThat(ex.detail()).isEqualTo(detail);
        assertThat(ex.getMessage()).isEqualTo("EXTERNAL_TIMEOUT: HTTP timeout 2000ms");
    }

    @Test
    @DisplayName("RuntimeException 을 상속한다 (unchecked)")
    void shouldExtendRuntimeException() {
        // when
        RandomClientException ex = new RandomClientException(AttemptFailureReason.EXTERNAL_UNKNOWN, "x");

        // then
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("detail 이 null 이어도 NPE 없이 'REASON: null' 포맷으로 메시지가 만들어진다")
    void shouldHandleNullDetail() {
        // when
        RandomClientException ex = new RandomClientException(AttemptFailureReason.EXTERNAL_UNKNOWN, null);

        // then
        assertThat(ex.detail()).isNull();
        assertThat(ex.getMessage()).isEqualTo("EXTERNAL_UNKNOWN: null");
    }

    @ParameterizedTest
    @EnumSource(AttemptFailureReason.class)
    @DisplayName("모든 AttemptFailureReason 으로 예외를 만들 수 있다")
    void shouldBeConstructibleForAnyReason(AttemptFailureReason reason) {
        // when
        RandomClientException ex = new RandomClientException(reason, "any detail");

        // then
        assertThat(ex.reason()).isEqualTo(reason);
        assertThat(ex.getMessage()).startsWith(reason.name() + ": ");
    }
}
