package com.ryuqqq.alt.application.subscription.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExternalCallResult — 외부 호출 결과 enum 단위 테스트")
class ExternalCallResultTest {

    @Test
    @DisplayName("APPROVED.isApproved() = true")
    void approvedShouldBeApproved() {
        assertThat(ExternalCallResult.APPROVED.isApproved()).isTrue();
    }

    @Test
    @DisplayName("REJECTED.isApproved() = false")
    void rejectedShouldNotBeApproved() {
        assertThat(ExternalCallResult.REJECTED.isApproved()).isFalse();
    }

    @Test
    @DisplayName("enum constant 가 두 개 (APPROVED / REJECTED) 만 존재한다")
    void shouldHaveExactlyTwoConstants() {
        ExternalCallResult[] values = ExternalCallResult.values();
        assertThat(values).hasSize(2);
        assertThat(values).containsExactly(ExternalCallResult.APPROVED, ExternalCallResult.REJECTED);
    }

    @Test
    @DisplayName("valueOf 로 이름 기반 enum 조회 — 직렬화/역직렬화 호환성 보장")
    void shouldResolveByValueOf() {
        assertThat(ExternalCallResult.valueOf("APPROVED")).isEqualTo(ExternalCallResult.APPROVED);
        assertThat(ExternalCallResult.valueOf("REJECTED")).isEqualTo(ExternalCallResult.REJECTED);
    }
}
