package com.ryuqqq.alt.adapter.in.subscription.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.ryuqqq.alt.adapter.in.subscription.dto.request.QuerySubscriptionHistoryApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.UnsubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.QuerySubscriptionHistoryApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.SubscribeApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.UnsubscribeApiResponse;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.AttemptStatus;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * SubscriptionApiMapper static 변환 유틸 단위 테스트.
 * Spring 컨텍스트 없이 순수 Java 검증.
 */
@DisplayName("SubscriptionApiMapper 변환 검증")
class SubscriptionApiMapperTest {

    private static final String VALID_PHONE = "01012345678";
    private static final Long CHANNEL_ID_VALUE = 7L;

    @Nested
    @DisplayName("toSubscribeCommand")
    class ToSubscribeCommand {

        @Test
        @DisplayName("요청 + idempotencyKey 가 Domain VO 로 매핑된다")
        void shouldMapRequestToSubscribeCommand() {
            SubscribeApiRequest request = new SubscribeApiRequest(
                VALID_PHONE, CHANNEL_ID_VALUE, SubscriptionStatus.PREMIUM
            );
            String idempotencyKey = "key-001";

            SubscribeCommand command = SubscriptionApiMapper.toSubscribeCommand(request, idempotencyKey);

            assertThat(command.phoneNumber().value()).isEqualTo(VALID_PHONE);
            assertThat(command.channelId().value()).isEqualTo(CHANNEL_ID_VALUE);
            assertThat(command.targetStatus()).isEqualTo(SubscriptionStatus.PREMIUM);
            assertThat(command.idempotencyKey()).isEqualTo(idempotencyKey);
        }

        @Test
        @DisplayName("idempotencyKey 가 null 이면 그대로 전달된다")
        void shouldPassNullIdempotencyKey() {
            SubscribeApiRequest request = new SubscribeApiRequest(
                VALID_PHONE, CHANNEL_ID_VALUE, SubscriptionStatus.BASIC
            );

            SubscribeCommand command = SubscriptionApiMapper.toSubscribeCommand(request, null);

            assertThat(command.idempotencyKey()).isNull();
        }

        @Test
        @DisplayName("하이픈/공백 포함 휴대폰 번호도 정규화되어 매핑된다")
        void shouldNormalizePhoneNumber() {
            SubscribeApiRequest request = new SubscribeApiRequest(
                "010-1234-5678", CHANNEL_ID_VALUE, SubscriptionStatus.BASIC
            );

            SubscribeCommand command = SubscriptionApiMapper.toSubscribeCommand(request, null);

            assertThat(command.phoneNumber().value()).isEqualTo("01012345678");
        }
    }

    @Nested
    @DisplayName("toSubscribeResponse")
    class ToSubscribeResponse {

        @Test
        @DisplayName("정상 결과는 attemptId/status/currentStatus/failureReason 모두 매핑된다")
        void shouldMapSuccessResult() {
            SubscribeResult result = new SubscribeResult(
                42L, AttemptStatus.COMMITTED, SubscriptionStatus.PREMIUM, null
            );

            SubscribeApiResponse response = SubscriptionApiMapper.toSubscribeResponse(result);

            assertThat(response.attemptId()).isEqualTo(42L);
            assertThat(response.status()).isEqualTo("COMMITTED");
            assertThat(response.currentStatus()).isEqualTo("PREMIUM");
            assertThat(response.failureReason()).isNull();
        }

        @Test
        @DisplayName("registrationOnly 결과는 status 가 null 인 상태로 매핑된다")
        void shouldMapRegistrationOnlyResult() {
            SubscribeResult result = SubscribeResult.registrationOnly(SubscriptionStatus.NONE);

            SubscribeApiResponse response = SubscriptionApiMapper.toSubscribeResponse(result);

            assertThat(response.attemptId()).isNull();
            assertThat(response.status()).isNull();
            assertThat(response.currentStatus()).isEqualTo("NONE");
            assertThat(response.failureReason()).isNull();
        }

        @Test
        @DisplayName("FAILED 결과는 failureReason 이 매핑된다")
        void shouldMapFailureReason() {
            SubscribeResult result = new SubscribeResult(
                9L, AttemptStatus.FAILED, SubscriptionStatus.NONE, "EXTERNAL_TIMEOUT"
            );

            SubscribeApiResponse response = SubscriptionApiMapper.toSubscribeResponse(result);

            assertThat(response.status()).isEqualTo("FAILED");
            assertThat(response.failureReason()).isEqualTo("EXTERNAL_TIMEOUT");
        }
    }

    @Nested
    @DisplayName("toUnsubscribeCommand")
    class ToUnsubscribeCommand {

        @Test
        @DisplayName("요청이 UnsubscribeCommand 로 매핑된다")
        void shouldMapRequestToUnsubscribeCommand() {
            UnsubscribeApiRequest request = new UnsubscribeApiRequest(
                VALID_PHONE, CHANNEL_ID_VALUE, SubscriptionStatus.NONE
            );

            UnsubscribeCommand command = SubscriptionApiMapper.toUnsubscribeCommand(request, "idem-99");

            assertThat(command.phoneNumber().value()).isEqualTo(VALID_PHONE);
            assertThat(command.channelId().value()).isEqualTo(CHANNEL_ID_VALUE);
            assertThat(command.targetStatus()).isEqualTo(SubscriptionStatus.NONE);
            assertThat(command.idempotencyKey()).isEqualTo("idem-99");
        }
    }

    @Nested
    @DisplayName("toUnsubscribeResponse")
    class ToUnsubscribeResponse {

        @Test
        @DisplayName("정상 결과가 응답 DTO 로 매핑된다")
        void shouldMapUnsubscribeResult() {
            UnsubscribeResult result = new UnsubscribeResult(
                100L, AttemptStatus.COMMITTED, SubscriptionStatus.NONE, null
            );

            UnsubscribeApiResponse response = SubscriptionApiMapper.toUnsubscribeResponse(result);

            assertThat(response.attemptId()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo("COMMITTED");
            assertThat(response.currentStatus()).isEqualTo("NONE");
            assertThat(response.failureReason()).isNull();
        }

        @Test
        @DisplayName("status 가 null 이어도 NPE 없이 매핑된다")
        void shouldHandleNullStatus() {
            UnsubscribeResult result = new UnsubscribeResult(
                null, null, SubscriptionStatus.BASIC, null
            );

            UnsubscribeApiResponse response = SubscriptionApiMapper.toUnsubscribeResponse(result);

            assertThat(response.status()).isNull();
            assertThat(response.currentStatus()).isEqualTo("BASIC");
        }
    }

    @Nested
    @DisplayName("toQuerySubscriptionHistoryQuery")
    class ToHistoryQuery {

        @Test
        @DisplayName("phoneNumber 만 사용하여 Query 가 생성된다")
        void shouldMapPhoneNumberToQuery() {
            QuerySubscriptionHistoryApiRequest request = new QuerySubscriptionHistoryApiRequest(VALID_PHONE);

            QuerySubscriptionHistoryQuery query = SubscriptionApiMapper.toQuerySubscriptionHistoryQuery(request);

            assertThat(query.phoneNumber().value()).isEqualTo(VALID_PHONE);
        }
    }

    @Nested
    @DisplayName("toHistoryResponse")
    class ToHistoryResponse {

        @Test
        @DisplayName("history 항목과 summary 가 모두 매핑된다")
        void shouldMapHistoryAndSummary() {
            Instant occurredAt = Instant.parse("2026-05-01T10:00:00Z");
            SubscriptionHistoryItemView item = new SubscriptionHistoryItemView(
                1L, 7L, "test-channel",
                AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE,
                SubscriptionStatus.BASIC,
                occurredAt
            );
            QuerySubscriptionHistoryResult result =
                QuerySubscriptionHistoryResult.of(List.of(item), "총 1건의 구독 이력이 있습니다");

            QuerySubscriptionHistoryApiResponse response = SubscriptionApiMapper.toHistoryResponse(result);

            assertThat(response.summary()).isEqualTo("총 1건의 구독 이력이 있습니다");
            assertThat(response.history()).hasSize(1);
            assertThat(response.history().get(0).attemptId()).isEqualTo(1L);
            assertThat(response.history().get(0).channelId()).isEqualTo(7L);
            assertThat(response.history().get(0).channelName()).isEqualTo("test-channel");
            assertThat(response.history().get(0).kind()).isEqualTo("SUBSCRIBE");
            assertThat(response.history().get(0).fromStatus()).isEqualTo("NONE");
            assertThat(response.history().get(0).toStatus()).isEqualTo("BASIC");
            assertThat(response.history().get(0).occurredAt()).isEqualTo(occurredAt);
        }

        @Test
        @DisplayName("이력이 빈 리스트면 빈 응답이 생성되며 summary 는 null 도 허용된다")
        void shouldMapEmptyHistoryWithNullSummary() {
            QuerySubscriptionHistoryResult result = QuerySubscriptionHistoryResult.withoutSummary(List.of());

            QuerySubscriptionHistoryApiResponse response = SubscriptionApiMapper.toHistoryResponse(result);

            assertThat(response.history()).isEmpty();
            assertThat(response.summary()).isNull();
        }

        @Test
        @DisplayName("여러 항목이 순서대로 매핑된다")
        void shouldPreserveOrder() {
            Instant t1 = Instant.parse("2026-05-01T10:00:00Z");
            Instant t2 = Instant.parse("2026-05-02T10:00:00Z");
            SubscriptionHistoryItemView item1 = new SubscriptionHistoryItemView(
                1L, 7L, "ch-1", AttemptKind.SUBSCRIBE,
                SubscriptionStatus.NONE, SubscriptionStatus.BASIC, t1
            );
            SubscriptionHistoryItemView item2 = new SubscriptionHistoryItemView(
                2L, 8L, "ch-2", AttemptKind.UNSUBSCRIBE,
                SubscriptionStatus.BASIC, SubscriptionStatus.NONE, t2
            );

            QuerySubscriptionHistoryResult result =
                QuerySubscriptionHistoryResult.of(List.of(item1, item2), "summary");

            QuerySubscriptionHistoryApiResponse response = SubscriptionApiMapper.toHistoryResponse(result);

            assertThat(response.history()).hasSize(2);
            assertThat(response.history().get(0).attemptId()).isEqualTo(1L);
            assertThat(response.history().get(1).attemptId()).isEqualTo(2L);
            assertThat(response.history().get(1).kind()).isEqualTo("UNSUBSCRIBE");
        }
    }
}
