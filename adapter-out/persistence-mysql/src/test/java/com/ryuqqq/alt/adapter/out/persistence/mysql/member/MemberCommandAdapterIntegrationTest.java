package com.ryuqqq.alt.adapter.out.persistence.mysql.member;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberQueryAdapter;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemberCommandAdapter 통합 테스트")
class MemberCommandAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    @Autowired
    private MemberQueryAdapter memberQueryAdapter;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("신규 persist (id == null)")
    class PersistNewMember {

        @Test
        @DisplayName("신규 회원 persist 시 DB 가 ID 를 채번해 반환한다")
        void persistNew_returnsGeneratedId() {
            Member toPersist = Member.forNew(PhoneNumber.of("01012340001"), SubscriptionStatus.NONE);

            Long id = memberCommandAdapter.persist(toPersist);

            assertThat(id).isNotNull().isPositive();
        }

        @Test
        @DisplayName("신규 persist 직후 같은 ID 로 다시 조회하면 동일 회원이 나온다")
        void persistNew_isImmediatelyQueryable() {
            Member toPersist = Member.forNew(PhoneNumber.of("01012340002"), SubscriptionStatus.PREMIUM);

            Long id = memberCommandAdapter.persist(toPersist);
            entityManager.flush();
            entityManager.clear();

            Optional<Member> found = memberQueryAdapter.findById(MemberId.of(id));
            assertThat(found).isPresent();
            assertThat(found.get().status()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(found.get().phoneNumber().value()).isEqualTo("01012340002");
        }
    }

    @Nested
    @DisplayName("phone_number UNIQUE 제약")
    class PhoneNumberUniqueConstraint {

        @Test
        @DisplayName("동일 phone_number 로 두 번 persist 하면 DataIntegrityViolationException 이 발생한다")
        void persist_duplicatePhoneNumber_throwsDataIntegrityViolation() {
            PhoneNumber phone = PhoneNumber.of("01099991111");
            memberCommandAdapter.persist(Member.forNew(phone, SubscriptionStatus.NONE));
            entityManager.flush();

            assertThatThrownBy(() -> {
                memberCommandAdapter.persist(Member.forNew(phone, SubscriptionStatus.BASIC));
                entityManager.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("기존 회원의 상태 변경 (reconstitute + persist)")
    class UpdateStatus {

        @Test
        @DisplayName("reconstitute 한 회원의 status 를 변경 후 persist 하면 그 변경이 반영된다")
        void persist_reconstitutedMember_appliesStatusChange() {
            // 1) 먼저 NONE 상태의 신규 회원을 영속화
            Member newMember = Member.forNew(PhoneNumber.of("01012340003"), SubscriptionStatus.NONE);
            Long id = memberCommandAdapter.persist(newMember);
            entityManager.flush();
            entityManager.clear();

            // 2) 영속 상태에서 reconstitute → applySubscribe(BASIC) → persist
            Member loaded = memberQueryAdapter.findById(MemberId.of(id)).orElseThrow();
            loaded.applySubscribe(SubscriptionStatus.BASIC);
            memberCommandAdapter.persist(loaded);
            entityManager.flush();
            entityManager.clear();

            // 3) 다시 조회했을 때 상태가 BASIC 으로 갱신되어 있어야 한다 (UPDATE 가 일어났는지 확인)
            Member afterUpdate = memberQueryAdapter.findById(MemberId.of(id)).orElseThrow();
            assertThat(afterUpdate.status()).isEqualTo(SubscriptionStatus.BASIC);
            assertThat(afterUpdate.id().value()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("Fixture 활용 — Member 도메인 다양한 상태")
    class FixtureBased {

        @Test
        @DisplayName("MemberFixture.newMember() 를 그대로 persist 할 수 있다")
        void persist_fromFixture_works() {
            Long id = memberCommandAdapter.persist(MemberFixture.newMember());

            assertThat(id).isPositive();
        }
    }
}
