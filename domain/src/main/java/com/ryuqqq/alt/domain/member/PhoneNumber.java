package com.ryuqqq.alt.domain.member;

import com.ryuqqq.alt.domain.error.InvalidPhoneNumberException;

import java.util.regex.Pattern;

/**
 * 한국 휴대폰 번호 VO. 입력 시 하이픈/공백을 제거한 정규화 형태로 보관 (예: 01012345678).
 *
 * 정책:
 * - 010/011/016/017/018/019 prefix, 총 10~11자리 숫자만 허용 (한국 표준)
 * - 글로벌 확장이 필요해지면 PhoneNumberValidator Port 를 분리해 어댑터로 교체할 수 있다.
 *   현재 과제 범위는 한국 시장으로 한정되므로 도메인에 정규식을 박아 둔다.
 *
 * 참조: DOM-VO-001 (record + compact constructor 검증), DOM-VO-003 (MAX_LENGTH 강제).
 */
public record PhoneNumber(String value) {

    public static final int MAX_LENGTH = 11;

    private static final Pattern KOREAN_MOBILE_PATTERN = Pattern.compile("^01[0-9]\\d{3,4}\\d{4}$");

    public PhoneNumber {
        if (value == null) {
            throw new InvalidPhoneNumberException("value is null");
        }
        value = value.replace("-", "").replace(" ", "");
        if (value.length() > MAX_LENGTH) {
            throw new InvalidPhoneNumberException("length > " + MAX_LENGTH + " : " + value);
        }
        if (!KOREAN_MOBILE_PATTERN.matcher(value).matches()) {
            throw new InvalidPhoneNumberException("not a Korean mobile number : " + value);
        }
    }

    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw);
    }
}
