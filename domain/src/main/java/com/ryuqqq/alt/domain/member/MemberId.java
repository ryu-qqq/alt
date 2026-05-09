package com.ryuqqq.alt.domain.member;

/**
 * Member Aggregate Root의 ID VO (DOM-ID-001).
 * value=null이면 신규(persist 직전), 양수면 영속화된 식별자.
 */
public record MemberId(Long value) {

    public MemberId {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("MemberId must be positive when present: " + value);
        }
    }

    public static MemberId of(Long value) {
        return new MemberId(value);
    }

    public static MemberId forNew() {
        return new MemberId(null);
    }

    public boolean isNew() {
        return value == null;
    }
}
