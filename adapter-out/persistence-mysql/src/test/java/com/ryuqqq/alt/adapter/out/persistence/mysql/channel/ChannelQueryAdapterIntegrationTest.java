package com.ryuqqq.alt.adapter.out.persistence.mysql.channel;

import com.ryuqqq.alt.adapter.out.persistence.mysql.AbstractPersistenceIntegrationTest;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.adapter.ChannelQueryAdapter;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.channel.ChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelQueryAdapter 통합 테스트")
class ChannelQueryAdapterIntegrationTest extends AbstractPersistenceIntegrationTest {

    @Autowired
    private ChannelQueryAdapter channelQueryAdapter;

    @Nested
    @DisplayName("Flyway 시드 채널 6건 검증")
    class FlywaySeedVerification {

        @Test
        @DisplayName("시드된 6개 채널 ID(1~6)가 모두 조회된다")
        void findByIds_returnsAllSeededChannels() {
            List<ChannelId> ids = List.of(
                ChannelId.of(1L), ChannelId.of(2L), ChannelId.of(3L),
                ChannelId.of(4L), ChannelId.of(5L), ChannelId.of(6L)
            );

            List<Channel> channels = channelQueryAdapter.findByIds(ids);

            assertThat(channels).hasSize(6);
            assertThat(channels)
                .extracting(Channel::name)
                .containsExactlyInAnyOrder("홈페이지", "모바일앱", "네이버", "SKT", "콜센터", "이메일");
        }

        @Test
        @DisplayName("시드된 채널 타입 분포: BOTH 2건 / SUBSCRIBE_ONLY 2건 / UNSUBSCRIBE_ONLY 2건")
        void seededChannels_haveExpectedTypeDistribution() {
            List<ChannelId> ids = List.of(
                ChannelId.of(1L), ChannelId.of(2L), ChannelId.of(3L),
                ChannelId.of(4L), ChannelId.of(5L), ChannelId.of(6L)
            );

            List<Channel> channels = channelQueryAdapter.findByIds(ids);

            long bothCount = channels.stream().filter(c -> c.type() == ChannelType.BOTH).count();
            long subscribeOnlyCount = channels.stream().filter(c -> c.type() == ChannelType.SUBSCRIBE_ONLY).count();
            long unsubscribeOnlyCount = channels.stream().filter(c -> c.type() == ChannelType.UNSUBSCRIBE_ONLY).count();

            assertThat(bothCount).isEqualTo(2);
            assertThat(subscribeOnlyCount).isEqualTo(2);
            assertThat(unsubscribeOnlyCount).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("존재하는 ID 로 조회하면 Channel 을 반환한다")
        void findById_existingId_returnsChannel() {
            Optional<Channel> result = channelQueryAdapter.findById(ChannelId.of(1L));

            assertThat(result).isPresent();
            assertThat(result.get().idValue()).isEqualTo(1L);
            assertThat(result.get().name()).isEqualTo("홈페이지");
            assertThat(result.get().type()).isEqualTo(ChannelType.BOTH);
        }

        @Test
        @DisplayName("존재하지 않는 ID 로 조회하면 Optional.empty 를 반환한다")
        void findById_nonExistingId_returnsEmpty() {
            Optional<Channel> result = channelQueryAdapter.findById(ChannelId.of(9999L));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsById")
    class ExistsById {

        @Test
        @DisplayName("존재하는 ID 면 true 를 반환한다")
        void existsById_existingId_returnsTrue() {
            assertThat(channelQueryAdapter.existsById(ChannelId.of(1L))).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 ID 면 false 를 반환한다")
        void existsById_nonExistingId_returnsFalse() {
            assertThat(channelQueryAdapter.existsById(ChannelId.of(9999L))).isFalse();
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {

        @Test
        @DisplayName("빈 컬렉션을 넘기면 빈 리스트를 반환한다 (가드 동작)")
        void findByIds_emptyCollection_returnsEmptyList() {
            List<Channel> result = channelQueryAdapter.findByIds(List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null 을 넘기면 빈 리스트를 반환한다")
        void findByIds_null_returnsEmptyList() {
            List<Channel> result = channelQueryAdapter.findByIds(null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하는 ID 와 존재하지 않는 ID 가 섞이면 존재하는 것만 반환한다")
        void findByIds_mixedIds_returnsOnlyExisting() {
            List<ChannelId> ids = List.of(ChannelId.of(1L), ChannelId.of(9999L), ChannelId.of(2L));

            List<Channel> result = channelQueryAdapter.findByIds(ids);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Channel::idValue).containsExactlyInAnyOrder(1L, 2L);
        }
    }
}
