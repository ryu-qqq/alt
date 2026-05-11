package com.ryuqqq.alt.adapter.out.persistence.mysql.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity.MemberJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.member.mapper.MemberEntityMapper;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemberEntityMapper 단위 테스트 — Domain ↔ Entity round-trip")
class MemberEntityMapperTest {

    private final MemberEntityMapper mapper = new MemberEntityMapper();

    @Nested
    @DisplayName("toEntity")
    class ToEntity {

        @Test
        @DisplayName("reconstitute 된 Member 의 모든 필드가 Entity 로 옮겨진다")
        void toEntity_preservesAllFields() {
            Member member = Member.reconstitute(
                MemberId.of(7L),
                PhoneNumber.of("01012345678"),
                SubscriptionStatus.PREMIUM
            );

            MemberJpaEntity entity = mapper.toEntity(member);

            assertThat(entity.getId()).isEqualTo(7L);
            assertThat(entity.getPhoneNumber()).isEqualTo("01012345678");
            assertThat(entity.getStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("forNew(id=null) Member 도 toEntity 호출이 가능하며 id=null 인 Entity 를 만든다")
        void toEntity_forNewMember_producesNullIdEntity() {
            Member member = Member.forNew(PhoneNumber.of("01000001111"), SubscriptionStatus.NONE);

            MemberJpaEntity entity = mapper.toEntity(member);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getPhoneNumber()).isEqualTo("01000001111");
            assertThat(entity.getStatus()).isEqualTo(SubscriptionStatus.NONE);
        }
    }

    @Nested
    @DisplayName("round-trip — toEntity → toDomain")
    class RoundTrip {

        @ParameterizedTest
        @EnumSource(SubscriptionStatus.class)
        @DisplayName("모든 SubscriptionStatus 에 대해 round-trip 결과가 원본과 동일하다")
        void roundTrip_allStatuses(SubscriptionStatus status) {
            Member original = MemberFixture.reconstitutedMemberWithStatus(status);

            Member roundTripped = mapper.toDomain(mapper.toEntity(original));

            assertThat(roundTripped.id().value()).isEqualTo(original.id().value());
            assertThat(roundTripped.phoneNumber().value()).isEqualTo(original.phoneNumber().value());
            assertThat(roundTripped.status()).isEqualTo(original.status());
        }
    }
}
