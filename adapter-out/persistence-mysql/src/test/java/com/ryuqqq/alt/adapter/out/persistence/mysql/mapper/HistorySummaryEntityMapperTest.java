package com.ryuqqq.alt.adapter.out.persistence.mysql.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.HistorySummaryJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.mapper.HistorySummaryEntityMapper;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HistorySummaryEntityMapper 단위 테스트 — Domain ↔ Entity round-trip")
class HistorySummaryEntityMapperTest {

    private final HistorySummaryEntityMapper mapper = new HistorySummaryEntityMapper();

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("HistorySummary 의 memberId / fingerprint / summary 가 Entity 로 옮겨진다")
        void toEntity_preservesAllFields() {
            HistorySummary domain = HistorySummary.of(MemberId.of(123L), 9999L, "요약 본문");

            HistorySummaryJpaEntity entity = mapper.toEntity(domain);

            assertThat(entity.getMemberId()).isEqualTo(123L);
            assertThat(entity.getFingerprint()).isEqualTo(9999L);
            assertThat(entity.getSummary()).isEqualTo("요약 본문");
        }
    }

    @Nested
    @DisplayName("round-trip — toEntity → toDomain")
    class RoundTrip {

        @Test
        @DisplayName("round-trip 결과가 원본과 동일하다")
        void roundTrip_preservesAllFields() {
            HistorySummary original = HistorySummary.of(MemberId.of(456L), 1L, "회원 #456 의 상태는 BASIC 입니다.");

            HistorySummary roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertThat(roundTripped.memberId().value()).isEqualTo(original.memberId().value());
            assertThat(roundTripped.fingerprint()).isEqualTo(original.fingerprint());
            assertThat(roundTripped.summary()).isEqualTo(original.summary());
        }

        @Test
        @DisplayName("긴 summary 도 손실 없이 round-trip 된다")
        void roundTrip_longSummary() {
            String longSummary = "요약 ".repeat(500);
            HistorySummary original = HistorySummary.of(MemberId.of(789L), 0L, longSummary);

            HistorySummary roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertThat(roundTripped.summary()).isEqualTo(longSummary);
        }
    }
}
