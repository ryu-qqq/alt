package com.ryuqqq.alt.domain.member;

import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;
import com.ryuqqq.alt.domain.error.SubscriptionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Member Aggregate")
class MemberTest {

    private static final PhoneNumber PHONE = PhoneNumber.of("01012345678");

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("forNew는 ID가 null이고 isNew=true")
        void forNewMember() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.NONE);
            assertThat(member.id().isNew()).isTrue();
            assertThat(member.phoneNumber()).isEqualTo(PHONE);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.NONE);
        }

        @Test
        @DisplayName("최초 회원도 PREMIUM으로 가입 가능")
        void canStartAsPremium() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.PREMIUM);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("reconstitute는 모든 필드를 보존")
        void reconstitute() {
            MemberId id = MemberId.of(100L);
            Member member = Member.reconstitute(id, PHONE, SubscriptionStatus.BASIC);
            assertThat(member.id()).isEqualTo(id);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.BASIC);
        }
    }

    @Nested
    @DisplayName("T-2. 구독 상태 변경")
    class SubscribeTransition {

        @Test
        @DisplayName("NONE → PREMIUM 성공")
        void noneToPremium() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.NONE);
            member.applySubscribe(SubscriptionStatus.PREMIUM);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("BASIC → PREMIUM 성공")
        void basicToPremium() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.BASIC);
            member.applySubscribe(SubscriptionStatus.PREMIUM);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("PREMIUM 상태에서 구독은 InvalidSubscribeTransitionException")
        void premiumCannotSubscribe() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.PREMIUM);
            assertThatThrownBy(() -> member.applySubscribe(SubscriptionStatus.PREMIUM))
                .isInstanceOf(InvalidSubscribeTransitionException.class)
                .satisfies(e -> assertThat(((InvalidSubscribeTransitionException) e).errorCode())
                    .isEqualTo(SubscriptionErrorCode.INVALID_SUBSCRIBE_TRANSITION));
        }

        @Test
        @DisplayName("BASIC → BASIC 같은 등급은 불가")
        void sameGradeRejected() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.BASIC);
            assertThatThrownBy(() -> member.applySubscribe(SubscriptionStatus.BASIC))
                .isInstanceOf(InvalidSubscribeTransitionException.class);
        }
    }

    @Nested
    @DisplayName("T-2. 해지 상태 변경")
    class UnsubscribeTransition {

        @Test
        @DisplayName("PREMIUM → NONE 성공")
        void premiumToNone() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.PREMIUM);
            member.applyUnsubscribe(SubscriptionStatus.NONE);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.NONE);
        }

        @Test
        @DisplayName("BASIC → NONE 성공")
        void basicToNone() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.BASIC);
            member.applyUnsubscribe(SubscriptionStatus.NONE);
            assertThat(member.status()).isEqualTo(SubscriptionStatus.NONE);
        }

        @Test
        @DisplayName("NONE 상태에서 해지는 InvalidUnsubscribeTransitionException")
        void noneCannotUnsubscribe() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.NONE);
            assertThatThrownBy(() -> member.applyUnsubscribe(SubscriptionStatus.NONE))
                .isInstanceOf(InvalidUnsubscribeTransitionException.class)
                .satisfies(e -> assertThat(((InvalidUnsubscribeTransitionException) e).errorCode())
                    .isEqualTo(SubscriptionErrorCode.INVALID_UNSUBSCRIBE_TRANSITION));
        }
    }

    @Nested
    @DisplayName("T-6. 동등성")
    class Equality {

        @Test
        @DisplayName("동일 ID면 equals=true")
        void sameId() {
            MemberId id = MemberId.of(1L);
            Member a = Member.reconstitute(id, PHONE, SubscriptionStatus.BASIC);
            Member b = Member.reconstitute(id, PHONE, SubscriptionStatus.PREMIUM);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("forNew끼리는 ID가 모두 null이라도 equals=false (id null 방어)")
        void forNewNotEqual() {
            Member a = Member.forNew(PHONE, SubscriptionStatus.NONE);
            Member b = Member.forNew(PHONE, SubscriptionStatus.NONE);
            // id.value() == null 인 두 객체는 동일 ID로 보지 않는다 (DOM-AGG-010)
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("자기 자신과 equals=true (reflexive)")
        void reflexive() {
            Member a = Member.reconstitute(MemberId.of(1L), PHONE, SubscriptionStatus.BASIC);

            assertThat(a).isEqualTo(a);
        }

        @Test
        @DisplayName("다른 타입과 equals=false")
        void differentTypeNotEqual() {
            Member a = Member.reconstitute(MemberId.of(1L), PHONE, SubscriptionStatus.BASIC);

            assertThat(a).isNotEqualTo("not a member");
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        @DisplayName("영속 회원과 forNew 회원은 equals=false (id null 분기)")
        void persistedVsNew() {
            Member persisted = Member.reconstitute(MemberId.of(1L), PHONE, SubscriptionStatus.NONE);
            Member fresh = Member.forNew(PHONE, SubscriptionStatus.NONE);

            assertThat(persisted).isNotEqualTo(fresh);
            assertThat(fresh).isNotEqualTo(persisted);
        }

        @Test
        @DisplayName("영속 ID 가 다르면 equals=false")
        void differentPersistedId() {
            Member a = Member.reconstitute(MemberId.of(1L), PHONE, SubscriptionStatus.NONE);
            Member b = Member.reconstitute(MemberId.of(2L), PHONE, SubscriptionStatus.NONE);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("T-2. withId — DB 채번 후 ID 주입")
    class WithId {

        @Test
        @DisplayName("forNew 인스턴스에 영속 ID 주입 — phoneNumber/status 보존")
        void assignsIdPreservingFields() {
            Member fresh = Member.forNew(PHONE, SubscriptionStatus.PREMIUM);
            MemberId persistedId = MemberId.of(42L);

            Member persisted = fresh.withId(persistedId);

            assertThat(persisted.id()).isEqualTo(persistedId);
            assertThat(persisted.id().isNew()).isFalse();
            assertThat(persisted.phoneNumber()).isEqualTo(PHONE);
            assertThat(persisted.status()).isEqualTo(SubscriptionStatus.PREMIUM);
        }

        @Test
        @DisplayName("새 인스턴스를 반환 — 원본은 isNew 유지")
        void returnsNewInstance() {
            Member fresh = Member.forNew(PHONE, SubscriptionStatus.NONE);

            Member persisted = fresh.withId(MemberId.of(7L));

            assertThat(persisted).isNotSameAs(fresh);
            assertThat(fresh.id().isNew()).isTrue();
        }
    }

    @Nested
    @DisplayName("T-4. toString — 식별자/상태만 노출 (PII 제외)")
    class ToStringFormat {

        @Test
        @DisplayName("영속 회원 toString 은 id 와 status displayName 노출")
        void persistedToString() {
            Member member = Member.reconstitute(MemberId.of(100L), PHONE, SubscriptionStatus.PREMIUM);

            String s = member.toString();

            assertThat(s).contains("Member{");
            assertThat(s).contains("id=100");
            assertThat(s).contains("status=프리미엄 구독");
            // PII 인 휴대폰 번호는 toString 에 포함되지 않아야 한다.
            assertThat(s).doesNotContain(PHONE.value());
            assertThat(s).doesNotContain("01012345678");
        }

        @Test
        @DisplayName("forNew 회원 toString 은 id=new 표기")
        void newToString() {
            Member member = Member.forNew(PHONE, SubscriptionStatus.BASIC);

            String s = member.toString();

            assertThat(s).contains("id=new");
            assertThat(s).contains("status=일반 구독");
        }
    }
}
