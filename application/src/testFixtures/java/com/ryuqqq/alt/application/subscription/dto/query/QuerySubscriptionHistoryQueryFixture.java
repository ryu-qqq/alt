package com.ryuqqq.alt.application.subscription.dto.query;

import com.ryuqqq.alt.domain.member.PhoneNumber;

/**
 * QuerySubscriptionHistoryQuery 테스트용 Fixture.
 */
public final class QuerySubscriptionHistoryQueryFixture {

    private QuerySubscriptionHistoryQueryFixture() {}

    public static final PhoneNumber DEFAULT_PHONE = PhoneNumber.of("01012345678");

    public static QuerySubscriptionHistoryQuery defaultQuery() {
        return QuerySubscriptionHistoryQuery.of(DEFAULT_PHONE);
    }

    public static QuerySubscriptionHistoryQuery of(PhoneNumber phoneNumber) {
        return QuerySubscriptionHistoryQuery.of(phoneNumber);
    }
}
