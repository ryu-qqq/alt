package com.ryuqqq.alt.adapter.in.common.response;

/**
 * 모든 컨트롤러 응답의 공통 래퍼.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data);
    }
}
