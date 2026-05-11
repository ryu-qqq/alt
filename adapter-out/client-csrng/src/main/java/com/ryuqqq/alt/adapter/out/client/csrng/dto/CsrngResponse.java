package com.ryuqqq.alt.adapter.out.client.csrng.dto;

/**
 * csrng API 응답 항목.
 *
 * 예시:
 * <pre>
 * [{ "status": "success", "min": 0, "max": 1, "random": 1 }]
 * </pre>
 */
public record CsrngResponse(
    String status,
    int min,
    int max,
    int random
) {
}
