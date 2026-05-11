package com.ryuqqq.alt.domain.subscription;

import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.member.MemberId;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_CHANNEL_ID;
import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_COMPLETED_AT;
import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_FAILURE_DETAIL;
import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_IDEMPOTENCY_KEY;
import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_MEMBER_ID;
import static com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture.DEFAULT_REQUESTED_AT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionAttempt 의 컨텍스트 보존 변환 메서드 검증.
 *
 * - withMemberId : DB 채번 후 memberId 만 갈아끼움
 * - asRolledBack : 외부 거절 시 ROLLED_BACK 으로 전환
 * - asFailed     : 외부 호출 실패 시 FAILED 로 전환 (사유/디테일 박제)
 *
 * 핵심: id / channelId / kind / fromStatus / toStatus / requestedAt / completedAt /
 *      idempotencyKey 등 컨텍스트는 모두 보존된다.
 */
@DisplayName("SubscriptionAttempt 변환 메서드 — 컨텍스트 보존")
class SubscriptionAttemptTransitionTest {

    @Nested
    @DisplayName("T-2. withMemberId — DB 채번 후 ID 주입")
    class WithMemberId {

        @Test
        @DisplayName("memberId 만 갈아끼우고 나머지 필드는 모두 보존")
        void replacesOnlyMemberId() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.committedSubscribe();
            MemberId newId = MemberId.of(7777L);

            SubscriptionAttempt swapped = original.withMemberId(newId);

            assertThat(swapped.memberId()).isEqualTo(newId);
            // 나머지 모든 필드 보존
            assertThat(swapped.id()).isEqualTo(original.id());
            assertThat(swapped.channelId()).isEqualTo(original.channelId());
            assertThat(swapped.kind()).isEqualTo(original.kind());
            assertThat(swapped.fromStatus()).isEqualTo(original.fromStatus());
            assertThat(swapped.toStatus()).isEqualTo(original.toStatus());
            assertThat(swapped.requestedAt()).isEqualTo(original.requestedAt());
            assertThat(swapped.completedAt()).isEqualTo(original.completedAt());
            assertThat(swapped.status()).isEqualTo(original.status());
            assertThat(swapped.failureReason()).isEqualTo(original.failureReason());
            assertThat(swapped.failureDetail()).isEqualTo(original.failureDetail());
            assertThat(swapped.idempotencyKey()).isEqualTo(original.idempotencyKey());
        }

        @Test
        @DisplayName("새 인스턴스를 반환 — 원본은 불변")
        void returnsNewInstance() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.committedSubscribe();
            MemberId originalMemberId = original.memberId();

            SubscriptionAttempt swapped = original.withMemberId(MemberId.of(99L));

            assertThat(swapped).isNotSameAs(original);
            assertThat(original.memberId()).isEqualTo(originalMemberId);
        }

        @Test
        @DisplayName("FAILED 상태에서도 memberId 만 갈아끼움 (failureReason/detail 보존)")
        void preservesFailedContext() {
            SubscriptionAttempt failed = SubscriptionAttemptFixture.failedSubscribe(AttemptFailureReason.EXTERNAL_TIMEOUT);

            SubscriptionAttempt swapped = failed.withMemberId(MemberId.of(42L));

            assertThat(swapped.memberId().value()).isEqualTo(42L);
            assertThat(swapped.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(swapped.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_TIMEOUT);
            assertThat(swapped.failureDetail()).isEqualTo(DEFAULT_FAILURE_DETAIL);
        }
    }

    @Nested
    @DisplayName("T-2. asRolledBack — 외부 거절 ROLLED_BACK 전환")
    class AsRolledBack {

        @Test
        @DisplayName("status=ROLLED_BACK, reason=EXTERNAL_REJECTED, detail=null")
        void transitionsToRolledBack() {
            SubscriptionAttempt committed = SubscriptionAttempt.committed(
                DEFAULT_MEMBER_ID, DEFAULT_CHANNEL_ID, AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.PREMIUM,
                DEFAULT_REQUESTED_AT, DEFAULT_COMPLETED_AT, DEFAULT_IDEMPOTENCY_KEY
            );

            SubscriptionAttempt rolledBack = committed.asRolledBack();

            assertThat(rolledBack.status()).isEqualTo(AttemptStatus.ROLLED_BACK);
            assertThat(rolledBack.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_REJECTED);
            assertThat(rolledBack.failureDetail()).isNull();
            assertThat(rolledBack.isCommitted()).isFalse();
        }

        @Test
        @DisplayName("memberId/channelId/timestamps/idempotencyKey/transition 컨텍스트 보존")
        void preservesContext() {
            SubscriptionAttempt committed = SubscriptionAttempt.reconstitute(
                AttemptId.of(123L), MemberId.of(55L), ChannelId.of(77L), AttemptKind.UNSUBSCRIBE,
                SubscriptionStatus.PREMIUM, SubscriptionStatus.BASIC,
                Instant.parse("2026-05-10T10:00:00Z"),
                Instant.parse("2026-05-10T10:00:02Z"),
                AttemptStatus.COMMITTED, null, null, "key-xyz"
            );

            SubscriptionAttempt rolledBack = committed.asRolledBack();

            assertThat(rolledBack.id().value()).isEqualTo(123L);
            assertThat(rolledBack.memberId().value()).isEqualTo(55L);
            assertThat(rolledBack.channelId().value()).isEqualTo(77L);
            assertThat(rolledBack.kind()).isEqualTo(AttemptKind.UNSUBSCRIBE);
            assertThat(rolledBack.fromStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(rolledBack.toStatus()).isEqualTo(SubscriptionStatus.BASIC);
            assertThat(rolledBack.requestedAt()).isEqualTo(Instant.parse("2026-05-10T10:00:00Z"));
            assertThat(rolledBack.completedAt()).isEqualTo(Instant.parse("2026-05-10T10:00:02Z"));
            assertThat(rolledBack.idempotencyKey()).isEqualTo("key-xyz");
        }

        @Test
        @DisplayName("새 인스턴스 반환 — 원본 불변")
        void returnsNewInstance() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.committedSubscribe();

            SubscriptionAttempt rolledBack = original.asRolledBack();

            assertThat(rolledBack).isNotSameAs(original);
            assertThat(original.status()).isEqualTo(AttemptStatus.COMMITTED);
        }
    }

    @Nested
    @DisplayName("T-2. asFailed — 외부 호출 실패 FAILED 전환")
    class AsFailed {

        @ParameterizedTest
        @EnumSource(value = AttemptFailureReason.class, names = {
            "EXTERNAL_TIMEOUT", "EXTERNAL_SERVER_ERROR", "EXTERNAL_CLIENT_ERROR",
            "EXTERNAL_CIRCUIT_OPEN", "EXTERNAL_PARSE_FAILURE", "EXTERNAL_UNKNOWN"
        })
        @DisplayName("어떤 외부 실패 사유든 FAILED 상태로 전환되며 reason/detail 박제")
        void transitionsToFailed(AttemptFailureReason reason) {
            SubscriptionAttempt committed = SubscriptionAttemptFixture.committedSubscribe();
            String detail = "boom: " + reason.name();

            SubscriptionAttempt failed = committed.asFailed(reason, detail);

            assertThat(failed.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(failed.failureReason()).isEqualTo(reason);
            assertThat(failed.failureDetail()).isEqualTo(detail);
            assertThat(failed.isCommitted()).isFalse();
        }

        @Test
        @DisplayName("memberId/channelId/timestamps/idempotencyKey 컨텍스트 보존")
        void preservesContext() {
            SubscriptionAttempt committed = SubscriptionAttempt.reconstitute(
                AttemptId.of(999L), MemberId.of(11L), ChannelId.of(22L), AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.BASIC,
                Instant.parse("2026-05-10T11:00:00Z"),
                Instant.parse("2026-05-10T11:00:03Z"),
                AttemptStatus.COMMITTED, null, null, "idem-key-A"
            );

            SubscriptionAttempt failed = committed.asFailed(AttemptFailureReason.EXTERNAL_CIRCUIT_OPEN, "circuit OPEN");

            assertThat(failed.id().value()).isEqualTo(999L);
            assertThat(failed.memberId().value()).isEqualTo(11L);
            assertThat(failed.channelId().value()).isEqualTo(22L);
            assertThat(failed.kind()).isEqualTo(AttemptKind.SUBSCRIBE);
            assertThat(failed.fromStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(failed.toStatus()).isEqualTo(SubscriptionStatus.BASIC);
            assertThat(failed.requestedAt()).isEqualTo(Instant.parse("2026-05-10T11:00:00Z"));
            assertThat(failed.completedAt()).isEqualTo(Instant.parse("2026-05-10T11:00:03Z"));
            assertThat(failed.idempotencyKey()).isEqualTo("idem-key-A");
        }

        @Test
        @DisplayName("ROLLED_BACK 상태에서도 FAILED 로 전환 가능 (사유/디테일 갱신)")
        void rolledBackToFailed() {
            SubscriptionAttempt rolledBack = SubscriptionAttemptFixture.rolledBackSubscribe();

            SubscriptionAttempt failed = rolledBack.asFailed(AttemptFailureReason.EXTERNAL_UNKNOWN, "post hoc reclassify");

            assertThat(failed.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(failed.failureReason()).isEqualTo(AttemptFailureReason.EXTERNAL_UNKNOWN);
            assertThat(failed.failureDetail()).isEqualTo("post hoc reclassify");
        }

        @Test
        @DisplayName("새 인스턴스 반환 — 원본 불변")
        void returnsNewInstance() {
            SubscriptionAttempt original = SubscriptionAttemptFixture.committedSubscribe();

            SubscriptionAttempt failed = original.asFailed(AttemptFailureReason.EXTERNAL_TIMEOUT, "timeout");

            assertThat(failed).isNotSameAs(original);
            assertThat(original.status()).isEqualTo(AttemptStatus.COMMITTED);
            assertThat(original.failureReason()).isNull();
            assertThat(original.failureDetail()).isNull();
        }
    }

    @Nested
    @DisplayName("T-4. Accessor 위임 — LoD 회피용 raw/displayName 접근자")
    class Accessors {

        @Test
        @DisplayName("memberId/channelId/idValue/channelIdValue raw value 노출")
        void rawValueAccessors() {
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.reconstitutedCommitted(42L);

            assertThat(attempt.idValue()).isEqualTo(42L);
            assertThat(attempt.channelIdValue()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_CHANNEL_ID.value());
            assertThat(attempt.memberId()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_MEMBER_ID);
            assertThat(attempt.channelId()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_CHANNEL_ID);
        }

        @Test
        @DisplayName("kindDisplayName/fromStatusDisplayName/toStatusDisplayName enum displayName 위임")
        void displayNameAccessors() {
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.committedSubscribe();

            // SUBSCRIBE("구독"), NONE("비구독"), PREMIUM("프리미엄") 와 같은 enum displayName 위임 검증.
            // 정확한 문자열은 enum 정의에 의존하므로 non-blank + 동등성으로 묶어 확인.
            assertThat(attempt.kindDisplayName()).isEqualTo(AttemptKind.SUBSCRIBE.displayName());
            assertThat(attempt.fromStatusDisplayName()).isEqualTo(SubscriptionStatus.NONE.displayName());
            assertThat(attempt.toStatusDisplayName()).isEqualTo(SubscriptionStatus.PREMIUM.displayName());
            assertThat(attempt.kindDisplayName()).isNotBlank();
        }

        @Test
        @DisplayName("requestedAt/completedAt/fromStatus/toStatus 표준 accessor 노출")
        void standardAccessors() {
            SubscriptionAttempt attempt = SubscriptionAttemptFixture.committedSubscribe();

            assertThat(attempt.requestedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
            assertThat(attempt.completedAt()).isEqualTo(DEFAULT_COMPLETED_AT);
            assertThat(attempt.fromStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(attempt.toStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
        }
    }

    @Nested
    @DisplayName("T-6. equals — null/타입 분기")
    class EqualsEdgeCases {

        @Test
        @DisplayName("자기 자신과 equals=true (reflexive)")
        void reflexive() {
            SubscriptionAttempt a = SubscriptionAttemptFixture.reconstitutedCommitted(1L);

            assertThat(a).isEqualTo(a);
        }

        @Test
        @DisplayName("다른 타입과 equals=false")
        void differentTypeNotEqual() {
            SubscriptionAttempt a = SubscriptionAttemptFixture.reconstitutedCommitted(1L);

            assertThat(a).isNotEqualTo("not an attempt");
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        @DisplayName("영속 attempt 와 forNew attempt 는 equals=false (id null 분기)")
        void persistedVsNew() {
            SubscriptionAttempt persisted = SubscriptionAttemptFixture.reconstitutedCommitted(1L);
            SubscriptionAttempt fresh = SubscriptionAttemptFixture.committedSubscribe();

            assertThat(persisted).isNotEqualTo(fresh);
            assertThat(fresh).isNotEqualTo(persisted);
        }

        @Test
        @DisplayName("영속 ID 가 다르면 equals=false")
        void differentPersistedId() {
            SubscriptionAttempt a = SubscriptionAttemptFixture.reconstitutedCommitted(1L);
            SubscriptionAttempt b = SubscriptionAttemptFixture.reconstitutedCommitted(2L);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("T-6. hashCode — ID 기반")
    class HashCodeBehavior {

        @Test
        @DisplayName("같은 영속 ID 면 hashCode 동일")
        void sameIdSameHash() {
            SubscriptionAttempt a = SubscriptionAttemptFixture.reconstitutedCommitted(7L);
            SubscriptionAttempt b = SubscriptionAttemptFixture.reconstitutedCommitted(7L);

            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("forNew (id null) 두 인스턴스의 hashCode 는 동일 — id 값이 같기 때문")
        void forNewHashCodeSame() {
            SubscriptionAttempt a = SubscriptionAttemptFixture.committedSubscribe();
            SubscriptionAttempt b = SubscriptionAttemptFixture.committedSubscribe();

            // forNew 끼리는 equals 는 false 지만 (DOM-AGG-010 방어), hashCode 는 id 기반이라
            // AttemptId(null) == AttemptId(null) 인 record 동등성에 의해 동일해야 한다.
            assertThat(a.id().isNew()).isTrue();
            assertThat(b.id().isNew()).isTrue();
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
