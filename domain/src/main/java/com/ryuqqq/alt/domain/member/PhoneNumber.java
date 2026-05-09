package com.ryuqqq.alt.domain.member;

import java.util.regex.Pattern;

/**
 * 한국 휴대폰 번호 VO. 입력 시 하이픈/공백을 제거한 정규화 형태로 보관 (예: 01012345678).
 * DOM-VO-001 (record + compact constructor 검증), DOM-VO-003 (MAX_LENGTH 강제).
 */
public record PhoneNumber(String value) {

    public static final int MAX_LENGTH = 11;

    private static final Pattern PATTERN = Pattern.compile("^01[0-9]\\d{3,4}\\d{4}$");

    public PhoneNumber {
        if (value == null) {
            throw new IllegalArgumentException("PhoneNumber must not be null");
        }
        value = value.replace("-", "").replace(" ", "");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "PhoneNumber length must be <= " + MAX_LENGTH + " : " + value);
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Korean mobile number: " + value);
        }
    }

    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw);
    }
}
