package com.ryuqqq.alt.application.subscription.port.out;

import java.util.function.Supplier;

/**
 * 멱등 키 등록 Port — 외부 호출 전 단의 idempotency short-circuit (ADR-0004 L1 강화).
 *
 * 보장:
 * - 동일 key 동시 N건 호출 시 {@code action} 은 1회만 실행 (Singleflight)
 * - 첫 호출자는 action 결과를 그대로 반환받음
 * - 이미 등록된 key 는 IdempotencyConflictException 으로 거절 (외부 호출 스킵)
 * - {@code action} 예외 throw 시 key 는 등록되지 않음 → 다음 호출에서 재시도 가능
 *
 * adapter-out 이 Caffeine 등으로 구현. 분산 환경 진화 시 Redis SETNX 어댑터로 교체.
 * application 은 본 인터페이스만 알면 된다.
 */
public interface IdempotencyRegistryPort {

    <T> T executeOnce(String key, Supplier<T> action);
}
