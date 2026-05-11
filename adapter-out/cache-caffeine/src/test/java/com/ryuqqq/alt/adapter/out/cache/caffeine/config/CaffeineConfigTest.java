package com.ryuqqq.alt.adapter.out.cache.caffeine.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CaffeineConfig 단위 테스트.
 * Bean 메서드가 properties 를 정확히 반영해 Caffeine 인스턴스를 만드는지 동작 기반으로 검증한다.
 */
class CaffeineConfigTest {

    @Test
    @DisplayName("idempotencyShortCircuitCache 빈을 생성하고 실제 put/get 이 동작한다")
    void shouldBuildCacheBean() {
        // given
        CaffeineProperties properties = new CaffeineProperties(
            new CaffeineProperties.Idempotency(Duration.ofSeconds(10), Duration.ofSeconds(1), 100L)
        );
        CaffeineConfig config = new CaffeineConfig(properties);

        // when
        Cache<String, Boolean> cache = config.idempotencyShortCircuitCache();

        // then — 빈 생성 + 기본 동작 검증
        assertThat(cache).isNotNull();
        cache.put("key-1", Boolean.TRUE);
        assertThat(cache.getIfPresent("key-1")).isTrue();
    }

    @Test
    @DisplayName("recordStats 가 활성화돼 stats() 가 호출 가능하다")
    void shouldRecordStats() {
        // given
        CaffeineProperties properties = new CaffeineProperties(
            new CaffeineProperties.Idempotency(Duration.ofSeconds(10), Duration.ZERO, 100L)
        );
        CaffeineConfig config = new CaffeineConfig(properties);
        Cache<String, Boolean> cache = config.idempotencyShortCircuitCache();

        // when
        cache.getIfPresent("missing"); // miss
        cache.put("hit-key", Boolean.TRUE);
        cache.getIfPresent("hit-key"); // hit

        // then
        assertThat(cache.stats().hitCount()).isEqualTo(1L);
        assertThat(cache.stats().missCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("maxSize 를 초과하면 evict 가 발생한다")
    void shouldEvictWhenMaxSizeExceeded() {
        // given — 매우 작은 maxSize 로 강제 evict
        CaffeineProperties properties = new CaffeineProperties(
            new CaffeineProperties.Idempotency(Duration.ofMinutes(10), Duration.ZERO, 2L)
        );
        CaffeineConfig config = new CaffeineConfig(properties);
        Cache<String, Boolean> cache = config.idempotencyShortCircuitCache();

        // when
        for (int i = 0; i < 10; i++) {
            cache.put("k-" + i, Boolean.TRUE);
        }
        cache.cleanUp();

        // then — 정확한 entry 수는 구현 디테일이지만 maxSize=2 에 근접해야 한다
        assertThat(cache.estimatedSize()).isLessThanOrEqualTo(5L);
    }

    @Test
    @DisplayName("jitter=0 인 경우에도 정상 동작한다 (jitter 분기 커버)")
    void shouldHandleZeroJitter() {
        // given
        CaffeineProperties properties = new CaffeineProperties(
            new CaffeineProperties.Idempotency(Duration.ofSeconds(60), Duration.ZERO, 50L)
        );
        CaffeineConfig config = new CaffeineConfig(properties);

        // when
        Cache<String, Boolean> cache = config.idempotencyShortCircuitCache();
        cache.put("k", Boolean.TRUE);

        // then
        assertThat(cache.getIfPresent("k")).isTrue();
    }
}
