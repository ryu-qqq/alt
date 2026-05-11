package com.ryuqqq.alt.adapter.out.cache.caffeine.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Caffeine 캐시 빈 정의 (단일 인스턴스 한정).
 *
 * - idempotencyShortCircuitCache : 멱등 키 1차 차단 (ADR-0004 L1 강화)
 *   TTL / jitter / maxSize 는 cache-caffeine.yml 의 cache-caffeine.idempotency.* 에 외부화.
 *
 * 분산 환경 전환 시 Redis SETNX 어댑터로 교체.
 */
@Configuration
@EnableConfigurationProperties(CaffeineProperties.class)
public class CaffeineConfig {

    private final CaffeineProperties properties;

    public CaffeineConfig(CaffeineProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Cache<String, Boolean> idempotencyShortCircuitCache() {
        CaffeineProperties.Idempotency idempotency = properties.idempotency();
        return Caffeine.newBuilder()
            .maximumSize(idempotency.maxSize())
            .expireAfter(jitteredExpiry(idempotency.ttl(), idempotency.jitter()))
            .recordStats()
            .build();
    }

    /**
     * entry 별 TTL = base + random(0..maxJitter).
     * 갱신·읽기 시 TTL 재계산 안 함 (sliding 비활성).
     */
    private static <K, V> Expiry<K, V> jitteredExpiry(Duration base, Duration maxJitter) {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(K key, V value, long currentTime) {
                long jitterNanos = maxJitter.isZero() ? 0L : ThreadLocalRandom.current().nextLong(maxJitter.toNanos());
                return base.toNanos() + jitterNanos;
            }

            @Override
            public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }
}
