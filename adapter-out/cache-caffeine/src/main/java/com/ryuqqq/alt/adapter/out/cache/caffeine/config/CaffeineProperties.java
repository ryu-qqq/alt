package com.ryuqqq.alt.adapter.out.cache.caffeine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * cache-caffeine.yml 의 cache-caffeine.* 설정 바인딩.
 *
 * - idempotency.ttl     : 멱등 키 1차 단락 캐시 entry TTL (base)
 * - idempotency.jitter  : entry 별 TTL 에 추가되는 random jitter (stampede 방어)
 * - idempotency.maxSize : 최대 entry 수 (메모리 상한)
 */
@ConfigurationProperties(prefix = "cache-caffeine")
public record CaffeineProperties(
    Idempotency idempotency
) {

    public CaffeineProperties {
        if (idempotency == null) {
            idempotency = new Idempotency(null, null, null);
        }
    }

    public record Idempotency(
        Duration ttl,
        Duration jitter,
        Long maxSize
    ) {
        public Idempotency {
            if (ttl == null) ttl = Duration.ofMinutes(5);
            if (jitter == null) jitter = Duration.ofMinutes(1);
            if (maxSize == null) maxSize = 200_000L;
        }
    }
}
