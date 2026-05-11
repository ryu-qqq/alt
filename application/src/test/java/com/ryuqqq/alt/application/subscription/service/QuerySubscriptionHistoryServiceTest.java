package com.ryuqqq.alt.application.subscription.service;

import com.ryuqqq.alt.application.subscription.assembler.SubscriptionHistoryAssembler;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.facade.HistorySummaryRefreshFacade;
import com.ryuqqq.alt.application.subscription.facade.SubscriptionHistoryReadFacade;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.member.PhoneNumber;
import com.ryuqqq.alt.domain.subscription.HistorySummary;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistoryFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuerySubscriptionHistoryService — 이력 조회 UseCase 단위 테스트")
class QuerySubscriptionHistoryServiceTest {

    @Mock SubscriptionHistoryReadFacade subscriptionHistoryReadFacade;
    @Mock HistorySummaryRefreshFacade historySummaryRefreshFacade;
    @Mock SubscriptionHistoryAssembler assembler;

    @InjectMocks QuerySubscriptionHistoryService service;

    private static final PhoneNumber PHONE = PhoneNumber.of("01012345678");

    private static QuerySubscriptionHistoryQuery query() {
        return QuerySubscriptionHistoryQuery.of(PHONE);
    }

    /** 헬퍼 — 임의 fingerprint 와 persistedSummary 를 직접 주입한 번들 빌드 */
    private static SubscriptionHistoryReadBundle bundleOf(
        Member member,
        SubscriptionHistory history,
        Channels channels,
        HistorySummary persistedSummary
    ) {
        return SubscriptionHistoryReadBundle.of(member, history, channels, persistedSummary);
    }

    @Nested
    @DisplayName("이력 없음(COMMITTED 0건)")
    class NoCommitted {

        @Test
        @DisplayName("hasCommitted=false 면 LLM 호출 없이 empty outcome 으로 assembler 를 통과한다")
        void shouldSkipLlmAndReturnEmptyWhenNoCommitted() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.empty();
            SubscriptionHistoryReadBundle bundle = bundleOf(member, history, Channels.empty(), null);

            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.withoutSummary(List.of());

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            given(assembler.toResult(eq(bundle), any(LlmSummaryOutcome.class))).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);

            verifyNoInteractions(historySummaryRefreshFacade);
            // assembler 에 전달된 outcome 이 empty (summary=null, unavailableReason=null) 인지 검증
            verify(assembler).toResult(eq(bundle), org.mockito.ArgumentMatchers.argThat(outcome ->
                outcome.summary() == null && outcome.unavailableReason() == null
            ));
        }

        @Test
        @DisplayName("COMMITTED 가 0건인 history 만 있어도 LLM 은 호출되지 않고 empty outcome 으로 assembler 를 통과한다 (rolled-back/failed 만 있는 경우)")
        void shouldSkipLlmWhenAllAttemptsAreNonCommitted() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.onlyNonCommitted();
            SubscriptionHistoryReadBundle bundle = bundleOf(member, history, Channels.empty(), null);

            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.withoutSummary(List.of());

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            given(assembler.toResult(eq(bundle), any(LlmSummaryOutcome.class))).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);

            verifyNoInteractions(historySummaryRefreshFacade);
            // assembler 에 전달된 outcome 이 empty (summary=null, unavailableReason=null) 인지 검증
            verify(assembler).toResult(eq(bundle), org.mockito.ArgumentMatchers.argThat(outcome ->
                outcome.summary() == null && outcome.unavailableReason() == null
            ));
        }
    }

    @Nested
    @DisplayName("캐시 히트 (영속 Summary fingerprint 일치)")
    class CacheHit {

        @Test
        @DisplayName("hasMatchingSummary=true 면 refreshFacade 를 호출하지 않고 영속 summary 를 그대로 사용한다")
        void shouldReturnPersistedSummaryWithoutCallingRefreshFacade() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            // singleCommitted 의 latestCommittedAttemptId = 1L → fingerprint = 1L
            HistorySummary persistedSummary = HistorySummary.of(member.id(), 1L, "캐시된 요약 한 줄");
            SubscriptionHistoryReadBundle bundle = bundleOf(
                member, history, ChannelFixture.defaultChannels(), persistedSummary
            );

            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.of(
                List.of(), "캐시된 요약 한 줄"
            );

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            // assembler 는 outcome.summary() 가 캐시된 값과 동일한 인스턴스인지 확인
            given(assembler.toResult(eq(bundle), any(LlmSummaryOutcome.class))).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);
            assertThat(bundle.hasMatchingSummary()).isTrue();

            verify(historySummaryRefreshFacade, never()).refresh(any());

            // assembler 에 전달된 outcome 이 isAvailable 이고 summary 가 캐시 그대로인지 검증
            verify(assembler).toResult(eq(bundle), org.mockito.ArgumentMatchers.argThat(outcome ->
                outcome.isAvailable() && "캐시된 요약 한 줄".equals(outcome.summary())
            ));
        }
    }

    @Nested
    @DisplayName("캐시 미스 (영속 Summary 없음 또는 fingerprint 불일치)")
    class CacheMiss {

        @Test
        @DisplayName("영속 Summary 가 없으면 refreshFacade.refresh 를 호출하고 그 결과로 assembler 가 결과를 조립한다")
        void shouldCallRefreshFacadeWhenNoPersistedSummary() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            SubscriptionHistoryReadBundle bundle = bundleOf(
                member, history, ChannelFixture.defaultChannels(), null
            );

            LlmSummaryOutcome refreshed = LlmSummaryOutcome.success("새로 요약된 한 줄");
            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.of(
                List.of(), "새로 요약된 한 줄"
            );

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            given(historySummaryRefreshFacade.refresh(bundle)).willReturn(refreshed);
            given(assembler.toResult(bundle, refreshed)).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);
            verify(historySummaryRefreshFacade, times(1)).refresh(bundle);
            verify(assembler, times(1)).toResult(bundle, refreshed);
        }

        @Test
        @DisplayName("fingerprint 가 다르면(stale) refreshFacade.refresh 를 호출한다")
        void shouldCallRefreshFacadeWhenFingerprintMismatch() {
            // given — singleCommitted 의 fingerprint=1L, 영속 summary 의 fingerprint=999L → mismatch
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            HistorySummary stale = HistorySummary.of(member.id(), 999L, "오래된 요약");
            SubscriptionHistoryReadBundle bundle = bundleOf(
                member, history, ChannelFixture.defaultChannels(), stale
            );
            assertThat(bundle.hasMatchingSummary()).isFalse(); // 가정 검증

            LlmSummaryOutcome refreshed = LlmSummaryOutcome.success("최신 요약");
            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.of(
                List.of(), "최신 요약"
            );

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            given(historySummaryRefreshFacade.refresh(bundle)).willReturn(refreshed);
            given(assembler.toResult(bundle, refreshed)).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);
            verify(historySummaryRefreshFacade).refresh(bundle);
            verify(assembler).toResult(bundle, refreshed);
        }

        @Test
        @DisplayName("LLM Unavailable 응답이어도 assembler 에 그대로 전달되어 결과 조립이 진행된다 (graceful degradation)")
        void shouldPassUnavailableOutcomeToAssembler() {
            // given
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.mixedStatuses();
            SubscriptionHistoryReadBundle bundle = bundleOf(
                member, history, ChannelFixture.defaultChannels(), null
            );

            LlmSummaryOutcome unavailable = LlmSummaryOutcome.unavailable("LLM_TIMEOUT");
            QuerySubscriptionHistoryResult assembled = QuerySubscriptionHistoryResult.withoutSummary(List.of());

            given(subscriptionHistoryReadFacade.findByPhoneNumber(PHONE)).willReturn(bundle);
            given(historySummaryRefreshFacade.refresh(bundle)).willReturn(unavailable);
            given(assembler.toResult(bundle, unavailable)).willReturn(assembled);

            // when
            QuerySubscriptionHistoryResult result = service.execute(query());

            // then
            assertThat(result).isSameAs(assembled);
            // unavailable outcome 도 assembler 에 그대로 전달됐는지 확인
            verify(assembler).toResult(eq(bundle), org.mockito.ArgumentMatchers.argThat(outcome ->
                !outcome.isAvailable() && "LLM_TIMEOUT".equals(outcome.unavailableReason())
            ));
        }
    }
}
