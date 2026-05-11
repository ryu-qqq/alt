package com.ryuqqq.alt.adapter.out.persistence.mysql.mapper;

import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.entity.ChannelJpaEntity;
import com.ryuqqq.alt.adapter.out.persistence.mysql.channel.mapper.ChannelEntityMapper;
import com.ryuqqq.alt.domain.channel.Channel;
import com.ryuqqq.alt.domain.channel.ChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelEntityMapper 단위 테스트 — Entity → Domain 매핑 정합성")
class ChannelEntityMapperTest {

    private final ChannelEntityMapper mapper = new ChannelEntityMapper();

    /**
     * ChannelEntityMapper 는 toEntity 가 없고 toDomain 만 존재 (도메인은 채널 쓰기 안 함).
     * 따라서 round-trip 대신 Entity 의 모든 필드 값이 Domain 에 그대로 매핑되는지를 reflection 으로 셋업해 검증.
     */
    @Test
    @DisplayName("BOTH 타입 채널 Entity → Domain 매핑 시 id/name/type 모두 보존된다")
    void toDomain_bothChannel_preservesAllFields() throws Exception {
        ChannelJpaEntity entity = newEntity(1L, "홈페이지", ChannelType.BOTH);

        Channel domain = mapper.toDomain(entity);

        assertThat(domain.idValue()).isEqualTo(1L);
        assertThat(domain.name()).isEqualTo("홈페이지");
        assertThat(domain.type()).isEqualTo(ChannelType.BOTH);
    }

    @Test
    @DisplayName("SUBSCRIBE_ONLY 타입 채널 매핑")
    void toDomain_subscribeOnlyChannel() throws Exception {
        ChannelJpaEntity entity = newEntity(3L, "네이버", ChannelType.SUBSCRIBE_ONLY);

        Channel domain = mapper.toDomain(entity);

        assertThat(domain.type()).isEqualTo(ChannelType.SUBSCRIBE_ONLY);
        assertThat(domain.canSubscribe()).isTrue();
        assertThat(domain.canUnsubscribe()).isFalse();
    }

    @Test
    @DisplayName("UNSUBSCRIBE_ONLY 타입 채널 매핑")
    void toDomain_unsubscribeOnlyChannel() throws Exception {
        ChannelJpaEntity entity = newEntity(5L, "콜센터", ChannelType.UNSUBSCRIBE_ONLY);

        Channel domain = mapper.toDomain(entity);

        assertThat(domain.type()).isEqualTo(ChannelType.UNSUBSCRIBE_ONLY);
        assertThat(domain.canSubscribe()).isFalse();
        assertThat(domain.canUnsubscribe()).isTrue();
    }

    /**
     * ChannelJpaEntity 는 protected no-arg + private 필드만 노출하므로 reflection 으로 셋업.
     */
    private static ChannelJpaEntity newEntity(Long id, String name, ChannelType type) throws Exception {
        java.lang.reflect.Constructor<ChannelJpaEntity> ctor = ChannelJpaEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        ChannelJpaEntity entity = ctor.newInstance();
        set(entity, "id", id);
        set(entity, "name", name);
        set(entity, "type", type);
        return entity;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
