package com.ryuqqq.alt.domain.member;

/**
 * Member BC 테스트용 Fixture.
 *
 * - 모든 Member 인스턴스는 forNew / reconstitute 생성자만 사용한다.
 * - DEFAULT_* 상수로 자주 쓰는 값을 통일해 테스트 가독성을 높인다.
 */
public final class MemberFixture {

    private MemberFixture() {}

    public static final long DEFAULT_ID = 1L;
    public static final PhoneNumber DEFAULT_PHONE = PhoneNumber.of("01012345678");

    public static Member newMember() {
        return Member.forNew(DEFAULT_PHONE, SubscriptionStatus.NONE);
    }

    public static Member newMemberWithStatus(SubscriptionStatus status) {
        return Member.forNew(DEFAULT_PHONE, status);
    }

    public static Member reconstitutedMember() {
        return Member.reconstitute(MemberId.of(DEFAULT_ID), DEFAULT_PHONE, SubscriptionStatus.NONE);
    }

    public static Member reconstitutedMemberWithStatus(SubscriptionStatus status) {
        return Member.reconstitute(MemberId.of(DEFAULT_ID), DEFAULT_PHONE, status);
    }

    public static Member reconstituted(long id, SubscriptionStatus status) {
        return Member.reconstitute(MemberId.of(id), DEFAULT_PHONE, status);
    }

    public static Member basicMember() {
        return reconstitutedMemberWithStatus(SubscriptionStatus.BASIC);
    }

    public static Member premiumMember() {
        return reconstitutedMemberWithStatus(SubscriptionStatus.PREMIUM);
    }
}
