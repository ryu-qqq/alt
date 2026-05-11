package com.ryuqqq.alt.adapter.out.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryuqqq.alt.domain.error.IdempotencyConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CaffeineIdempotencyRegistry 단위 테스트.
 *
 * 핵심 계약:
 * - 정상 호출: action 1회 실행, 반환값 그대로 전달
 * - 동일 key 재호출: 두 번째 호출의 action 은 실행되지 않고 IdempotencyConflictException
 * - 동일 key 동시 호출: action 은 정확히 1회만 실행 (Caffeine singleflight 보장)
 * - action 이 예외 → cache 저장 X → 다음 호출 정상 진행 가능
 * - 서로 다른 key 는 독립적
 */
class CaffeineIdempotencyRegistryTest {

    private Cache<String, Boolean> cache;
    private CaffeineIdempotencyRegistry registry;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1_000)
            .build();
        registry = new CaffeineIdempotencyRegistry(cache);
    }

    @Nested
    @DisplayName("정상 흐름")
    class Success {

        @Test
        @DisplayName("최초 호출 시 action 을 1회 실행하고 반환값을 그대로 돌려준다")
        void shouldExecuteActionOnceOnFirstCall() {
            // given
            AtomicInteger counter = new AtomicInteger();

            // when
            String result = registry.executeOnce("key-1", () -> {
                counter.incrementAndGet();
                return "result";
            });

            // then
            assertThat(result).isEqualTo("result");
            assertThat(counter).hasValue(1);
        }

        @Test
        @DisplayName("서로 다른 key 는 각각 독립적으로 action 을 실행한다")
        void shouldHandleDifferentKeysIndependently() {
            // given
            AtomicInteger counter = new AtomicInteger();

            // when
            String r1 = registry.executeOnce("k-1", () -> {
                counter.incrementAndGet();
                return "v1";
            });
            String r2 = registry.executeOnce("k-2", () -> {
                counter.incrementAndGet();
                return "v2";
            });

            // then
            assertThat(r1).isEqualTo("v1");
            assertThat(r2).isEqualTo("v2");
            assertThat(counter).hasValue(2);
        }
    }

    @Nested
    @DisplayName("중복 차단")
    class IdempotencyConflict {

        @Test
        @DisplayName("동일 key 로 두 번째 호출 시 IdempotencyConflictException 으로 거절한다")
        void shouldThrowConflictOnSecondCallWithSameKey() {
            // given
            AtomicInteger counter = new AtomicInteger();
            registry.executeOnce("dup-key", () -> {
                counter.incrementAndGet();
                return "first";
            });

            // when & then
            assertThatThrownBy(() -> registry.executeOnce("dup-key", () -> {
                counter.incrementAndGet();
                return "second";
            }))
                .isInstanceOf(IdempotencyConflictException.class);
            // 두 번째 action 은 실행되지 않아야 한다
            assertThat(counter).hasValue(1);
        }

        @Test
        @DisplayName("IdempotencyConflictException 메시지에 key 가 포함된다")
        void shouldIncludeKeyInExceptionMessage() {
            // given
            registry.executeOnce("trace-key", () -> "ok");

            // when & then
            assertThatThrownBy(() -> registry.executeOnce("trace-key", () -> "second"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("trace-key");
        }
    }

    @Nested
    @DisplayName("예외 시 캐시 미저장")
    class ActionThrows {

        @Test
        @DisplayName("action 이 예외를 던지면 cache 에 저장되지 않아 다음 호출이 정상 진행된다")
        void shouldNotCacheWhenActionThrows() {
            // given
            AtomicInteger counter = new AtomicInteger();

            // when — 첫 호출 예외
            assertThatThrownBy(() -> registry.executeOnce("retry-key", () -> {
                counter.incrementAndGet();
                throw new IllegalStateException("first fail");
            }))
                .isInstanceOf(IllegalStateException.class);

            // 두 번째 호출은 정상 진행 가능
            String result = registry.executeOnce("retry-key", () -> {
                counter.incrementAndGet();
                return "recovered";
            });

            // then
            assertThat(result).isEqualTo("recovered");
            assertThat(counter).hasValue(2);
        }
    }

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("동일 key 로 동시 호출되면 action 은 정확히 1회만 실행되고 나머지는 IdempotencyConflictException")
        void shouldExecuteActionOnceOnConcurrentCalls() throws InterruptedException {
            // given
            int threads = 16;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(threads);
            AtomicInteger actionExecutions = new AtomicInteger();
            ConcurrentLinkedQueue<String> successes = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            try {
                for (int i = 0; i < threads; i++) {
                    pool.submit(() -> {
                        try {
                            startGate.await();
                            String r = registry.executeOnce("concurrent-key", () -> {
                                actionExecutions.incrementAndGet();
                                // 약간의 work 흉내 — singleflight 검증 신호 강화
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                                return "winner";
                            });
                            successes.add(r);
                        } catch (Throwable t) {
                            failures.add(t);
                        } finally {
                            doneGate.countDown();
                        }
                    });
                }
                startGate.countDown();
                boolean finished = doneGate.await(5, TimeUnit.SECONDS);
                assertThat(finished).isTrue();
            } finally {
                pool.shutdownNow();
            }

            // then
            assertThat(actionExecutions).hasValue(1);
            // Caffeine singleflight: 동일 key 동시 호출 시 모든 caller 가 동일 mapping 결과를 받는다 (Boolean.TRUE).
            // 우리 구현은 mappingFunction 안에서 action.get() 을 호출한 thread 만 holder 에 값 기록 → 그 thread 만 winner 반환.
            // 그 외 thread 는 holder 가 null 이라 IdempotencyConflictException.
            assertThat(successes).hasSize(1);
            assertThat(successes).containsExactly("winner");
            assertThat(failures).hasSize(threads - 1);
            assertThat(failures).allMatch(t -> t instanceof IdempotencyConflictException);
        }
    }

    @Nested
    @DisplayName("반환 타입 일반화")
    class GenericReturn {

        @Test
        @DisplayName("non-String 반환값도 그대로 전달한다")
        void shouldPropagateNonStringReturn() {
            // when
            List<Integer> result = registry.executeOnce("list-key", () -> List.of(1, 2, 3));

            // then
            assertThat(result).containsExactly(1, 2, 3);
        }
    }
}
