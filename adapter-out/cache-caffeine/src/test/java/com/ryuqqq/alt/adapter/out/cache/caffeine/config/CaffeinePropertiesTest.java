package com.ryuqqq.alt.adapter.out.cache.caffeine.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CaffeineProperties 단위 테스트.
 * record + nested record 의 defaults 가 정상 동작하는지 검증.
 */
class CaffeinePropertiesTest {

    @Nested
    @DisplayName("Idempotency 중첩 record defaults")
    class IdempotencyDefaults {

        @Test
        @DisplayName("ttl/jitter/maxSize 가 모두 null 이면 기본값으로 채워진다")
        void shouldApplyDefaultsWhenAllFieldsNull() {
            // when
            CaffeineProperties.Idempotency idempotency = new CaffeineProperties.Idempotency(null, null, null);

            // then
            assertThat(idempotency.ttl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(idempotency.jitter()).isEqualTo(Duration.ofMinutes(1));
            assertThat(idempotency.maxSize()).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("명시적 값이 주어지면 그 값을 유지한다")
        void shouldKeepExplicitValues() {
            // given
            Duration ttl = Duration.ofSeconds(30);
            Duration jitter = Duration.ofSeconds(5);
            long maxSize = 1_000L;

            // when
            CaffeineProperties.Idempotency idempotency = new CaffeineProperties.Idempotency(ttl, jitter, maxSize);

            // then
            assertThat(idempotency.ttl()).isEqualTo(ttl);
            assertThat(idempotency.jitter()).isEqualTo(jitter);
            assertThat(idempotency.maxSize()).isEqualTo(maxSize);
        }

        @Test
        @DisplayName("일부 필드만 null 이면 해당 필드만 기본값으로 채워진다")
        void shouldFillOnlyNullFields() {
            // given
            Duration ttl = Duration.ofSeconds(30);

            // when
            CaffeineProperties.Idempotency idempotency = new CaffeineProperties.Idempotency(ttl, null, null);

            // then
            assertThat(idempotency.ttl()).isEqualTo(ttl);
            assertThat(idempotency.jitter()).isEqualTo(Duration.ofMinutes(1));
            assertThat(idempotency.maxSize()).isEqualTo(200_000L);
        }
    }

    @Nested
    @DisplayName("최상위 CaffeineProperties defaults")
    class TopLevelDefaults {

        @Test
        @DisplayName("idempotency 가 null 이면 기본 Idempotency 가 생성된다")
        void shouldCreateDefaultIdempotencyWhenNull() {
            // when
            CaffeineProperties properties = new CaffeineProperties(null);

            // then
            assertThat(properties.idempotency()).isNotNull();
            assertThat(properties.idempotency().ttl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.idempotency().jitter()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.idempotency().maxSize()).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("idempotency 가 명시되면 그대로 보존된다")
        void shouldKeepProvidedIdempotency() {
            // given
            CaffeineProperties.Idempotency idempotency = new CaffeineProperties.Idempotency(
                Duration.ofMinutes(10), Duration.ofSeconds(30), 50_000L
            );

            // when
            CaffeineProperties properties = new CaffeineProperties(idempotency);

            // then
            assertThat(properties.idempotency()).isSameAs(idempotency);
        }
    }
}
