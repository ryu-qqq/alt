package com.ryuqqq.alt.application.subscription.dto.query;

import com.ryuqqq.alt.domain.member.PhoneNumber;

public record QuerySubscriptionHistoryQuery(
    PhoneNumber phoneNumber
) {

    public static QuerySubscriptionHistoryQuery of(PhoneNumber phoneNumber) {
        return new QuerySubscriptionHistoryQuery(phoneNumber);
    }
}
