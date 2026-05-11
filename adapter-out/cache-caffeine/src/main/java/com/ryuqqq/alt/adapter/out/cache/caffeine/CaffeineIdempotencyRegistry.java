package com.ryuqqq.alt.adapter.out.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.ryuqqq.alt.application.subscription.port.out.IdempotencyRegistryPort;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Caffeine 기반 IdempotencyRegistryPort 구현 (단일 인스턴스).
 *
 * Caffeine.get(key, mappingFunction) 보장:
 * - 동일 key 동시 호출 시 mappingFunction 은 1회만 실행 (Singleflight, key 단위 lock)
 * - 정상 반환 → atomic put
 * - 예외 throw → put 안 됨 (다음 호출 재시도 가능)
 *
 * 우리 규약: 두 번째 호출자는 mappingFunction 이 실행되지 않으므로 holder 가 비어있다.
 * 이 경우 IdempotencyConflictException 으로 거절 → ErrorMapper 가 HTTP 409 변환.
 *
 * 분산 환경 진화 시 Redis SETNX 어댑터로 교체.
 */
@Component
public class CaffeineIdempotencyRegistry implements IdempotencyRegistryPort {

    private final Cache<String, Boolean> cache;

    public CaffeineIdempotencyRegistry(Cache<String, Boolean> idempotencyShortCircuitCache) {
        this.cache = idempotencyShortCircuitCache;
    }

    @Override
    public <T> T executeOnce(String key, Supplier<T> action) {
        AtomicReference<T> holder = new AtomicReference<>();
        cache.get(key, k -> {
            holder.set(action.get());
            return Boolean.TRUE;
        });
        T captured = holder.get();
        if (captured == null) {
            throw new IdempotencyConflictException(key);
        }
        return captured;
    }
}
