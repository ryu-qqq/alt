package com.ryuqqq.alt.adapter.out.persistence.mysql.member;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberCommandAdapter;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.adapter.MemberQueryAdapter;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberQueryAdapter 통합 테스트")
class MemberQueryAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private MemberQueryAdapter memberQueryAdapter;

    @Autowired
    private MemberCommandAdapter memberCommandAdapter;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("존재하지 않는 ID 로 조회하면 Optional.empty 를 반환한다")
        void findById_nonExisting_returnsEmpty() {
            Optional<Member> result = memberQueryAdapter.findById(MemberId.of(99_999L));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("영속화된 회원을 ID 로 조회하면 동일한 도메인 객체를 반환한다")
        void findById_existing_returnsMember() {
            Member persisted = MemberFixture.newMember();
            Long id = memberCommandAdapter.persist(persisted);

            Optional<Member> result = memberQueryAdapter.findById(MemberId.of(id));

            assertThat(result).isPresent();
            assertThat(result.get().id().value()).isEqualTo(id);
            assertThat(result.get().phoneNumber()).isEqualTo(MemberFixture.DEFAULT_PHONE);
            assertThat(result.get().status()).isEqualTo(SubscriptionStatus.NONE);
        }
    }

    @Nested
    @DisplayName("findByPhoneNumber")
    class FindByPhoneNumber {

        @Test
        @DisplayName("존재하지 않는 번호로 조회하면 Optional.empty 를 반환한다")
        void findByPhoneNumber_nonExisting_returnsEmpty() {
            Optional<Member> result = memberQueryAdapter.findByPhoneNumber(PhoneNumber.of("01099999999"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("영속화된 번호로 조회하면 회원을 반환한다")
        void findByPhoneNumber_existing_returnsMember() {
            PhoneNumber phone = PhoneNumber.of("01011112222");
            Member toPersist = Member.forNew(phone, SubscriptionStatus.BASIC);
            Long id = memberCommandAdapter.persist(toPersist);

            Optional<Member> result = memberQueryAdapter.findByPhoneNumber(phone);

            assertThat(result).isPresent();
            assertThat(result.get().id().value()).isEqualTo(id);
            assertThat(result.get().status()).isEqualTo(SubscriptionStatus.BASIC);
        }
    }

    @Nested
    @DisplayName("existsByPhoneNumber")
    class ExistsByPhoneNumber {

        @Test
        @DisplayName("존재하지 않는 번호면 false 를 반환한다")
        void existsByPhoneNumber_nonExisting_returnsFalse() {
            assertThat(memberQueryAdapter.existsByPhoneNumber(PhoneNumber.of("01088888888"))).isFalse();
        }

        @Test
        @DisplayName("영속화된 번호면 true 를 반환한다")
        void existsByPhoneNumber_existing_returnsTrue() {
            PhoneNumber phone = PhoneNumber.of("01077777777");
            memberCommandAdapter.persist(Member.forNew(phone, SubscriptionStatus.NONE));

            assertThat(memberQueryAdapter.existsByPhoneNumber(phone)).isTrue();
        }
    }
}
