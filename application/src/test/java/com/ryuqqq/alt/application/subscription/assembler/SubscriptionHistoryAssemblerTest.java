package com.ryuqqq.alt.application.subscription.assembler;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.dto.response.SubscriptionHistoryItemView;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberFixture;
import com.ryuqqq.alt.domain.subscription.AttemptKind;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttemptFixture;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistory;
import com.ryuqqq.alt.domain.subscription.SubscriptionHistoryFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubscriptionHistoryAssembler — Bundle + Outcome → 응답 조립 단위 테스트")
class SubscriptionHistoryAssemblerTest {

    private final SubscriptionHistoryAssembler assembler = new SubscriptionHistoryAssembler();

    @Nested
    @DisplayName("이력 0건")
    class NoCommitted {

        @Test
        @DisplayName("committed 가 0건이면 history 리스트는 비어있고 summary 는 outcome.empty().summary() = null")
        void shouldReturnEmptyHistoryAndNullSummary() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.empty();
            LlmSummaryOutcome empty = LlmSummaryOutcome.empty();

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, empty);

            // then
            assertThat(result.history()).isEmpty();
            assertThat(result.summary()).isNull();
        }
    }

    @Nested
    @DisplayName("committed N건 + LLM 결과 분기")
    class WithCommitted {

        @Test
        @DisplayName("singleCommitted + success outcome → view 1건 + summary 본문")
        void shouldBuildSingleViewWithSummary() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome success = LlmSummaryOutcome.success("최신 요약");

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, success);

            // then
            assertThat(result.history()).hasSize(1);
            assertThat(result.summary()).isEqualTo("최신 요약");

            SubscriptionHistoryItemView view = result.history().get(0);
            assertThat(view.attemptId()).isEqualTo(1L);
            assertThat(view.kind()).isEqualTo(AttemptKind.SUBSCRIBE);
        }

        @Test
        @DisplayName("mixedStatuses + success outcome → COMMITTED 만 노출 (rolled_back/failed 제외)")
        void shouldOnlyExposeCommittedAttempts() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.mixedCommittedNoSummary();
            LlmSummaryOutcome success = LlmSummaryOutcome.success("요약 본문");

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, success);

            // then — mixedStatuses 는 attemptId 1L (commit, channel 11), 2L (rolled), 3L (failed), 4L (commit, channel 12)
            // committed 만 노출되므로 2건
            assertThat(result.history()).hasSize(2);
            assertThat(result.history()).extracting(SubscriptionHistoryItemView::attemptId)
                .containsExactly(1L, 4L);
            assertThat(result.summary()).isEqualTo("요약 본문");
        }

        @Test
        @DisplayName("singleCommitted + empty outcome → view 1건 + summary null")
        void shouldKeepViewsWhenEmptyOutcome() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome empty = LlmSummaryOutcome.empty();

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, empty);

            // then
            assertThat(result.history()).hasSize(1);
            assertThat(result.summary()).isNull();
        }

        @Test
        @DisplayName("singleCommitted + unavailable outcome → view 1건 + summary null (graceful degradation)")
        void shouldKeepViewsWhenUnavailableOutcome() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();
            LlmSummaryOutcome unavailable = LlmSummaryOutcome.unavailable("LLM_TIMEOUT");

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, unavailable);

            // then
            assertThat(result.history()).hasSize(1);
            assertThat(result.summary()).isNull();
        }
    }

    @Nested
    @DisplayName("채널 매핑")
    class ChannelMapping {

        @Test
        @DisplayName("committed attempt 의 channelId 와 매칭되는 Channel 이 있으면 view.channelName 에 그 이름이 매핑된다")
        void shouldMapChannelNameWhenChannelExists() {
            // given — singleCommitted 는 channelId=10L 의 attempt
            // 명시적으로 channelId=10L 인 채널을 포함한 Channels 빌드
            Channels channels = Channels.from(List.of(
                ChannelFixture.reconstituted(10L, "특별 채널", com.ryuqqq.alt.domain.channel.ChannelType.BOTH)
            ));
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(member, history, channels, null);

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, LlmSummaryOutcome.empty());

            // then
            assertThat(result.history()).hasSize(1);
            SubscriptionHistoryItemView view = result.history().get(0);
            assertThat(view.channelName()).isEqualTo("특별 채널");
            assertThat(view.channelId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("committed attempt 의 channelId 와 매칭되는 Channel 이 없으면 channelName 은 빈 문자열")
        void shouldUseEmptyStringWhenChannelMissing() {
            // given — channels 에는 10L 채널이 없음
            Channels channels = Channels.empty();
            Member member = MemberFixture.reconstitutedMember();
            SubscriptionHistory history = SubscriptionHistoryFixture.singleCommitted();
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(member, history, channels, null);

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, LlmSummaryOutcome.empty());

            // then
            assertThat(result.history()).hasSize(1);
            assertThat(result.history().get(0).channelName()).isEmpty();
        }
    }

    @Nested
    @DisplayName("view 매핑 정확성")
    class ViewFieldMapping {

        @Test
        @DisplayName("attempt 의 모든 필드가 view 에 정확히 매핑된다")
        void shouldMapAllAttemptFieldsToView() {
            // given — 단일 commit attempt 로 매핑 검증
            Member member = MemberFixture.reconstitutedMember();
            // ChannelId 11L 의 commit attempt (kind=SUBSCRIBE, fromStatus=NONE, toStatus=PREMIUM)
            SubscriptionHistory history = SubscriptionHistory.of(
                member.id(),
                List.of(SubscriptionAttemptFixture.reconstitutedCommitted(7L, ChannelId.of(11L)))
            );
            Channels channels = ChannelFixture.defaultChannels();
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundle.of(member, history, channels, null);

            // when
            QuerySubscriptionHistoryResult result = assembler.toResult(bundle, LlmSummaryOutcome.empty());

            // then
            SubscriptionHistoryItemView view = result.history().get(0);
            assertThat(view.attemptId()).isEqualTo(7L);
            assertThat(view.channelId()).isEqualTo(11L);
            assertThat(view.channelName()).isEqualTo(ChannelFixture.SUBSCRIBE_ONLY_NAME);
            assertThat(view.kind()).isEqualTo(AttemptKind.SUBSCRIBE);
            assertThat(view.fromStatus()).isEqualTo(com.ryuqqq.alt.domain.member.SubscriptionStatus.NONE);
            assertThat(view.toStatus()).isEqualTo(com.ryuqqq.alt.domain.member.SubscriptionStatus.PREMIUM);
            assertThat(view.occurredAt()).isEqualTo(SubscriptionAttemptFixture.DEFAULT_COMPLETED_AT);
        }
    }
}
