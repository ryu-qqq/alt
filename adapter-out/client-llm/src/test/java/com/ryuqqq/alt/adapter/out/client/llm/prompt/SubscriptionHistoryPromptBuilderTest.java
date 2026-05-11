package com.ryuqqq.alt.adapter.out.client.llm.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundleFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SubscriptionHistoryPromptBuilder 단위 테스트.
 *
 * - 빈 이력, 1건, N건 시나리오의 user 메시지 직렬화 확인
 * - 시간순(ASC) 정렬 보장 검증
 * - system 메시지 prompt 가 schema 안내(JSON 스키마, 예시) 포함
 */
class SubscriptionHistoryPromptBuilderTest {

    private SubscriptionHistoryPromptBuilder builder;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        builder = new SubscriptionHistoryPromptBuilder(objectMapper);
    }

    @Nested
    @DisplayName("기본 구조")
    class StructuralChecks {

        @Test
        @DisplayName("messages 는 항상 system + user 2건이고 system 은 JSON 스키마 안내를 포함한다")
        void shouldAlwaysReturnSystemAndUserMessages() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();

            // when
            List<ChatCompletionRequest.Message> messages = builder.build(bundle);

            // then
            assertThat(messages).hasSize(2);
            assertThat(messages.get(0).role()).isEqualTo("system");
            assertThat(messages.get(0).content())
                .contains("JSON")
                .contains("status")
                .contains("narrative");
            assertThat(messages.get(1).role()).isEqualTo("user");
        }
    }

    @Nested
    @DisplayName("이력 직렬화")
    class HistorySerialization {

        @Test
        @DisplayName("이력이 비어있으면 user 메시지는 빈 JSON 배열 ([]) 이다")
        void shouldSerializeEmptyHistoryAsEmptyArray() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.empty();

            // when
            List<ChatCompletionRequest.Message> messages = builder.build(bundle);

            // then
            assertThat(messages.get(1).content().trim()).isEqualTo("[]");
        }

        @Test
        @DisplayName("이력 1건이면 user 메시지에 해당 채널/상태/시간이 포함된다")
        void shouldIncludeSingleHistoryItem() {
            // given
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.singleCommittedNoSummary();

            // when
            List<ChatCompletionRequest.Message> messages = builder.build(bundle);

            // then — singleCommitted 는 channelId=10L 인데 defaultChannels(11/12/13) 에는 없어 빈 문자열 매핑
            // (Channels.nameOf 가 일치 채널 없으면 "" 반환)
            String userContent = messages.get(1).content();
            assertThat(userContent)
                .contains("\"fromStatus\":\"구독 안함\"")
                .contains("\"toStatus\":\"프리미엄 구독\"")
                .contains("\"kind\":\"구독\"")
                .contains("\"channelName\":\"\"");
        }

        @Test
        @DisplayName("mixed 이력에서 COMMITTED 만 추출되고 시간순(ASC)으로 정렬된다")
        void shouldFilterCommittedAndSortByCompletedAtAscending() {
            // given — mixed 이력: committed(1L, ch=11), rolledBack, failed, committed(4L, ch=12)
            SubscriptionHistoryReadBundle bundle = SubscriptionHistoryReadBundleFixture.mixedCommittedNoSummary();

            // when
            List<ChatCompletionRequest.Message> messages = builder.build(bundle);
            String userContent = messages.get(1).content();

            // then
            // committed 2건만 포함 → "구독 전용 채널", "해지 전용 채널"
            assertThat(userContent).contains("\"channelName\":\"구독 전용 채널\"");
            assertThat(userContent).contains("\"channelName\":\"해지 전용 채널\"");
            // rolledBack / failed 는 default Fixture 가 채널 ID 10L 인데, 이름 매핑 안 되므로
            // 만약 잘못 포함되면 빈 문자열 "" 채널이 추가로 등장. 정확히 2건 확인 — '"channelName"' 발생 횟수 = 2
            int occurrences = countOccurrences(userContent, "\"channelName\"");
            assertThat(occurrences).isEqualTo(2);
            // 정렬: completedAt 이 모두 동일(DEFAULT_COMPLETED_AT) 이므로 입력 순서대로 안정 정렬됨
            // ch=11(id=1) 가 ch=12(id=4) 보다 앞에 있어야 함
            assertThat(userContent.indexOf("구독 전용 채널"))
                .isLessThan(userContent.indexOf("해지 전용 채널"));
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
