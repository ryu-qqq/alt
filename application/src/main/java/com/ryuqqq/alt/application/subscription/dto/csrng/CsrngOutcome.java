package com.ryuqqq.alt.application.subscription.dto.csrng;

/**
 * csrng 호출 결과. sealed 로 어댑터의 모든 가능한 결과를 강제 표현.
 *
 * - Success(random=1) : 정상 처리, attempt commit
 * - Success(random=0) : csrng 거절, attempt rollback
 * - Unavailable        : 타임아웃 / 5xx / circuit open / 파싱 실패 등 — attempt fail
 *
 * 어댑터(client-csrng) 는 Resilience4j 정책 적용 결과를 이 sealed 타입으로 변환해 반환한다.
 * 즉 application 레이어에서는 csrng 어댑터의 예외를 직접 다루지 않는다.
 */
public sealed interface CsrngOutcome {

    record Success(int random) implements CsrngOutcome {

        public Success {
            if (random != 0 && random != 1) {
                throw new IllegalArgumentException("csrng random must be 0 or 1: " + random);
            }
        }

        public boolean isCommitSignal() {
            return random == 1;
        }
    }

    record Unavailable(String reason) implements CsrngOutcome { }
}
