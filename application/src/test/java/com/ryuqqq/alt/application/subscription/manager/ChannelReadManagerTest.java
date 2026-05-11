package com.ryuqqq.alt.application.subscription.manager;

import com.ryuqqq.alt.application.subscription.port.out.ChannelQueryPort;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelFixture;
import com.ryuqqq.alt.domain.channel.ChannelId;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.error.ChannelNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelReadManager — 채널 조회 매니저 단위 테스트")
class ChannelReadManagerTest {

    @Mock ChannelQueryPort channelQueryPort;

    @InjectMocks ChannelReadManager manager;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("존재하는 channelId 로 조회하면 Channel 을 반환한다")
        void shouldReturnChannelWhenExists() {
            // given
            Channel channel = ChannelFixture.bothChannel();
            given(channelQueryPort.findById(channel.id())).willReturn(Optional.of(channel));

            // when
            Channel result = manager.getById(channel.id());

            // then
            assertThat(result).isSameAs(channel);
        }

        @Test
        @DisplayName("존재하지 않는 channelId 로 조회하면 ChannelNotFoundException 을 던진다")
        void shouldThrowChannelNotFoundWhenAbsent() {
            // given
            ChannelId missing = ChannelId.of(999L);
            given(channelQueryPort.findById(missing)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> manager.getById(missing))
                .isInstanceOf(ChannelNotFoundException.class)
                .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("verifyExists")
    class VerifyExists {

        @Test
        @DisplayName("채널이 존재하면 예외 없이 통과한다")
        void shouldNotThrowWhenExists() {
            // given
            ChannelId id = ChannelId.of(13L);
            given(channelQueryPort.existsById(id)).willReturn(true);

            // when & then
            manager.verifyExists(id);
            // 예외 없이 통과
        }

        @Test
        @DisplayName("채널이 존재하지 않으면 ChannelNotFoundException 을 던진다")
        void shouldThrowWhenAbsent() {
            // given
            ChannelId id = ChannelId.of(404L);
            given(channelQueryPort.existsById(id)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> manager.verifyExists(id))
                .isInstanceOf(ChannelNotFoundException.class)
                .hasMessageContaining("404");
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {

        @Test
        @DisplayName("null 입력은 Channels.empty() 를 반환하고 port 는 호출되지 않는다")
        void shouldReturnEmptyWhenNull() {
            // when
            Channels result = manager.findByIds(null);

            // then
            assertThat(result.isEmpty()).isTrue();
            verifyNoInteractions(channelQueryPort);
        }

        @Test
        @DisplayName("빈 컬렉션 입력은 Channels.empty() 를 반환하고 port 는 호출되지 않는다")
        void shouldReturnEmptyWhenCollectionEmpty() {
            // when
            Channels result = manager.findByIds(List.of());

            // then
            assertThat(result.isEmpty()).isTrue();
            verifyNoInteractions(channelQueryPort);
        }

        @Test
        @DisplayName("정상 컬렉션 입력은 port 결과를 Channels.from 으로 감싸 반환한다")
        void shouldReturnChannelsWrappedFromPortResult() {
            // given
            List<ChannelId> ids = List.of(ChannelId.of(11L), ChannelId.of(12L));
            List<Channel> channels = List.of(
                ChannelFixture.subscribeOnlyChannel(),
                ChannelFixture.unsubscribeOnlyChannel()
            );
            given(channelQueryPort.findByIds(ids)).willReturn(channels);

            // when
            Channels result = manager.findByIds(ids);

            // then
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.findById(ChannelId.of(11L))).isPresent();
            assertThat(result.findById(ChannelId.of(12L))).isPresent();
        }
    }
}
