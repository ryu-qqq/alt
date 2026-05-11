package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.HistorySummaryCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.HistorySummaryQueryAdapter;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HistorySummaryQueryAdapter 통합 테스트")
class HistorySummaryQueryAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private HistorySummaryQueryAdapter historySummaryQueryAdapter;

    @Autowired
    private HistorySummaryCommandAdapter historySummaryCommandAdapter;

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    private Long persistMember(String phone) {
        return memberCommandAdapter.persist(Member.forNew(PhoneNumber.of(phone), SubscriptionStatus.NONE));
    }

    @Nested
    @DisplayName("find(memberId)")
    class FindByMemberId {

        @Test
        @DisplayName("저장된 적 없는 회원이면 Optional.empty 를 반환한다")
        void find_nonExisting_returnsEmpty() {
            Long memberId = persistMember("01072000001");

            Optional<HistorySummary> result = historySummaryQueryAdapter.find(MemberId.of(memberId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("영속된 회원의 요약을 조회하면 동일한 fingerprint / summary 가 반환된다")
        void find_existing_returnsSameFingerprintAndSummary() {
            Long memberId = persistMember("01072000002");
            long fingerprint = 12345L;
            String summary = "회원 #" + memberId + " 의 최신 구독 상태는 PREMIUM 입니다.";

            historySummaryCommandAdapter.persist(
                HistorySummary.of(MemberId.of(memberId), fingerprint, summary)
            );

            Optional<HistorySummary> result = historySummaryQueryAdapter.find(MemberId.of(memberId));

            assertThat(result).isPresent();
            assertThat(result.get().memberId().value()).isEqualTo(memberId);
            assertThat(result.get().fingerprint()).isEqualTo(fingerprint);
            assertThat(result.get().summary()).isEqualTo(summary);
        }
    }
}
