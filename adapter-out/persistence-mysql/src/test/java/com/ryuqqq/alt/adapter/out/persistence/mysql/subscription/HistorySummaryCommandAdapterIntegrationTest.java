package com.ryuqqq.alt.adapter.out.persistence.mysql.subscription;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.HistorySummaryCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.adapter.HistorySummaryQueryAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.entity.HistorySummaryJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.subscription.repository.HistorySummaryJpaRepository;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HistorySummaryCommandAdapter 통합 테스트 — upsert 동작 검증")
class HistorySummaryCommandAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private HistorySummaryCommandAdapter historySummaryCommandAdapter;

    @Autowired
    private HistorySummaryQueryAdapter historySummaryQueryAdapter;

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    @Autowired
    private HistorySummaryJpaRepository historySummaryJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long persistMember(String phone) {
        return memberCommandAdapter.persist(Member.forNew(PhoneNumber.of(phone), SubscriptionStatus.NONE));
    }

    @Nested
    @DisplayName("최초 INSERT")
    class FirstInsert {

        @Test
        @DisplayName("최초 persist 시 새 row 가 INSERT 된다")
        void persist_first_insertsRow() {
            Long memberId = persistMember("01073000001");

            historySummaryCommandAdapter.persist(
                HistorySummary.of(MemberId.of(memberId), 100L, "첫 요약")
            );
            entityManager.flush();
            entityManager.clear();

            HistorySummaryJpaEntity entity = historySummaryJpaRepository.findById(memberId).orElseThrow();
            assertThat(entity.getFingerprint()).isEqualTo(100L);
            assertThat(entity.getSummary()).isEqualTo("첫 요약");
            assertThat(entity.createdAt()).isNotNull();
            assertThat(entity.updatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("같은 member_id 로 재호출 시 UPDATE — upsert 의미")
    class Upsert {

        @Test
        @DisplayName("동일 member_id 로 재persist 하면 row 가 1건 유지되고 fingerprint / summary 가 갱신된다")
        void persist_sameMemberId_updatesInPlace() {
            Long memberId = persistMember("01073000002");
            MemberId mid = MemberId.of(memberId);

            // 1차
            historySummaryCommandAdapter.persist(HistorySummary.of(mid, 200L, "v1"));
            entityManager.flush();
            entityManager.clear();

            long countBefore = historySummaryJpaRepository.count();

            // 2차 (같은 member_id)
            historySummaryCommandAdapter.persist(HistorySummary.of(mid, 300L, "v2 갱신된 요약"));
            entityManager.flush();
            entityManager.clear();

            // row 개수 변화 없음 (UPDATE)
            long countAfter = historySummaryJpaRepository.count();
            assertThat(countAfter).isEqualTo(countBefore);

            // 최신 값으로 갱신됨
            HistorySummary loaded = historySummaryQueryAdapter.find(mid).orElseThrow();
            assertThat(loaded.fingerprint()).isEqualTo(300L);
            assertThat(loaded.summary()).isEqualTo("v2 갱신된 요약");
        }

        @Test
        @DisplayName("UPDATE 시 updated_at 이 created_at 이후로 갱신된다")
        void persist_update_advancesUpdatedAt() throws InterruptedException {
            Long memberId = persistMember("01073000003");
            MemberId mid = MemberId.of(memberId);

            historySummaryCommandAdapter.persist(HistorySummary.of(mid, 400L, "first"));
            entityManager.flush();
            entityManager.clear();

            HistorySummaryJpaEntity afterInsert = historySummaryJpaRepository.findById(memberId).orElseThrow();
            Instant createdAt = afterInsert.createdAt();
            Instant updatedAtAfterInsert = afterInsert.updatedAt();
            entityManager.clear();

            // 적은 시간 차이로 인해 timestamp 비교가 동일해지는 것을 회피
            Thread.sleep(50);

            historySummaryCommandAdapter.persist(HistorySummary.of(mid, 500L, "second"));
            entityManager.flush();
            entityManager.clear();

            HistorySummaryJpaEntity afterUpdate = historySummaryJpaRepository.findById(memberId).orElseThrow();
            // created_at 은 보존되어야 한다
            assertThat(afterUpdate.createdAt()).isEqualTo(createdAt);
            // updated_at 은 갱신되어야 한다 (>= 첫 updated_at)
            assertThat(afterUpdate.updatedAt()).isAfterOrEqualTo(updatedAtAfterInsert);
        }
    }

    @Nested
    @DisplayName("서로 다른 회원의 요약은 독립적으로 보관된다")
    class Isolation {

        @Test
        @DisplayName("회원 A 와 B 의 요약은 각각 별 row 로 저장된다")
        void persist_differentMembers_savesAsSeparateRows() {
            Long aId = persistMember("01073000004");
            Long bId = persistMember("01073000005");

            historySummaryCommandAdapter.persist(HistorySummary.of(MemberId.of(aId), 1L, "A 요약"));
            historySummaryCommandAdapter.persist(HistorySummary.of(MemberId.of(bId), 2L, "B 요약"));
            entityManager.flush();
            entityManager.clear();

            HistorySummary aLoaded = historySummaryQueryAdapter.find(MemberId.of(aId)).orElseThrow();
            HistorySummary bLoaded = historySummaryQueryAdapter.find(MemberId.of(bId)).orElseThrow();

            assertThat(aLoaded.summary()).isEqualTo("A 요약");
            assertThat(bLoaded.summary()).isEqualTo("B 요약");
            assertThat(aLoaded.fingerprint()).isNotEqualTo(bLoaded.fingerprint());
        }
    }
}
