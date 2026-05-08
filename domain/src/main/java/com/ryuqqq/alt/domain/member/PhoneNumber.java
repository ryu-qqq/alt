package com.ryuqqq.alt.domain.member;

import java.util.regex.Pattern;

public record PhoneNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^01[0-9]\\d{3,4}\\d{4}$");

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("PhoneNumber must not be null");
        }
        // 정규화: 하이픈/공백 제거 후 검증
        value = value.replace("-", "").replace(" ", "");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Korean mobile number: " + value);
        }
    }
}
