package com.ryuqqq.alt.domain.channel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Channel Aggregate")
class ChannelTest {

    @Nested
    @DisplayName("T-1. 생성")
    class Creation {

        @Test
        @DisplayName("forNew는 ID가 isNew=true")
        void forNewChannel() {
            Channel channel = Channel.forNew("홈페이지", ChannelType.BOTH);
            assertThat(channel.id().isNew()).isTrue();
            assertThat(channel.name()).isEqualTo("홈페이지");
            assertThat(channel.type()).isEqualTo(ChannelType.BOTH);
        }

        @Test
        @DisplayName("reconstitute는 모든 필드를 보존")
        void reconstitute() {
            ChannelId id = ChannelId.of(10L);
            Channel channel = Channel.reconstitute(id, "콜센터", ChannelType.UNSUBSCRIBE_ONLY);
            assertThat(channel.id()).isEqualTo(id);
            assertThat(channel.idValue()).isEqualTo(10L);
            assertThat(channel.name()).isEqualTo("콜센터");
            assertThat(channel.type()).isEqualTo(ChannelType.UNSUBSCRIBE_ONLY);
        }
    }

    @Nested
    @DisplayName("T-4. 권한 위임 (LoD)")
    class PermissionDelegation {

        @Test
        @DisplayName("BOTH 채널은 구독/해지 모두 가능")
        void bothAllows() {
            Channel channel = Channel.forNew("모바일앱", ChannelType.BOTH);
            assertThat(channel.canSubscribe()).isTrue();
            assertThat(channel.canUnsubscribe()).isTrue();
        }

        @Test
        @DisplayName("SUBSCRIBE_ONLY 채널은 구독만 가능")
        void subscribeOnly() {
            Channel channel = Channel.forNew("네이버", ChannelType.SUBSCRIBE_ONLY);
            assertThat(channel.canSubscribe()).isTrue();
            assertThat(channel.canUnsubscribe()).isFalse();
        }

        @Test
        @DisplayName("typeDisplayName은 enum displayName 위임")
        void typeDisplayName() {
            Channel channel = Channel.forNew("이메일", ChannelType.UNSUBSCRIBE_ONLY);
            assertThat(channel.typeDisplayName()).isEqualTo("해지 전용");
        }
    }

    @Nested
    @DisplayName("T-6. 동등성 — ID 기반")
    class Equality {

        @Test
        @DisplayName("동일 영속 ID 면 equals=true (이름/타입이 달라도)")
        void sameIdEqual() {
            ChannelId id = ChannelId.of(10L);
            Channel a = Channel.reconstitute(id, "이메일", ChannelType.SUBSCRIBE_ONLY);
            Channel b = Channel.reconstitute(id, "콜센터", ChannelType.UNSUBSCRIBE_ONLY);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("자기 자신과 equals=true (reflexive)")
        void reflexive() {
            Channel a = Channel.reconstitute(ChannelId.of(10L), "이메일", ChannelType.BOTH);

            assertThat(a).isEqualTo(a);
        }

        @Test
        @DisplayName("다른 클래스 인스턴스와 equals=false")
        void differentTypeNotEqual() {
            Channel a = Channel.reconstitute(ChannelId.of(10L), "이메일", ChannelType.BOTH);

            assertThat(a).isNotEqualTo("not a channel");
            assertThat(a).isNotEqualTo(null);
        }

        @Test
        @DisplayName("forNew (ID 없음) 끼리는 equals=false — id null 방어")
        void forNewNotEqual() {
            Channel a = Channel.forNew("A", ChannelType.BOTH);
            Channel b = Channel.forNew("A", ChannelType.BOTH);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("영속 채널과 forNew 채널은 equals=false")
        void persistedVsNew() {
            Channel persisted = Channel.reconstitute(ChannelId.of(10L), "A", ChannelType.BOTH);
            Channel fresh = Channel.forNew("A", ChannelType.BOTH);

            assertThat(persisted).isNotEqualTo(fresh);
            assertThat(fresh).isNotEqualTo(persisted);
        }

        @Test
        @DisplayName("영속 ID 가 다르면 equals=false")
        void differentPersistedId() {
            Channel a = Channel.reconstitute(ChannelId.of(10L), "A", ChannelType.BOTH);
            Channel b = Channel.reconstitute(ChannelId.of(20L), "A", ChannelType.BOTH);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("forNew hashCode 는 NPE 없이 동작 — Objects.hashCode 는 null 안전")
        void forNewHashCodeNoNpe() {
            Channel a = Channel.forNew("A", ChannelType.BOTH);
            Channel b = Channel.forNew("A", ChannelType.BOTH);

            // ChannelId(null) record 의 hashCode 는 두 인스턴스에서 동일
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    @Nested
    @DisplayName("T-4. toString")
    class ToStringFormat {

        @Test
        @DisplayName("영속 채널 toString 은 id, name, type displayName 노출")
        void persistedToString() {
            Channel channel = Channel.reconstitute(ChannelId.of(10L), "이메일", ChannelType.UNSUBSCRIBE_ONLY);

            String s = channel.toString();

            assertThat(s).contains("Channel{");
            assertThat(s).contains("id=10");
            assertThat(s).contains("name=이메일");
            assertThat(s).contains("type=해지 전용");
        }

        @Test
        @DisplayName("forNew 채널 toString 은 id=new 표기")
        void newToString() {
            Channel channel = Channel.forNew("이메일", ChannelType.BOTH);

            String s = channel.toString();

            assertThat(s).contains("id=new");
            assertThat(s).contains("name=이메일");
        }
    }
}
