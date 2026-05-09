package com.ryuqqq.alt.domain.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MemberId 패턴 (ID VO 대표)")
class MemberIdTest {

    @Test
    @DisplayName("T-5. of(value) 정상 생성")
    void of() {
        MemberId id = MemberId.of(1L);
        assertThat(id.value()).isEqualTo(1L);
        assertThat(id.isNew()).isFalse();
    }

    @Test
    @DisplayName("T-5. forNew()는 isNew=true")
    void forNew() {
        MemberId id = MemberId.forNew();
        assertThat(id.value()).isNull();
        assertThat(id.isNew()).isTrue();
    }

    @Test
    @DisplayName("T-5. of(null)도 isNew=true (DOM-VO-004)")
    void ofNull() {
        MemberId id = MemberId.of(null);
        assertThat(id.isNew()).isTrue();
    }

    @Test
    @DisplayName("T-5. 음수/0은 거부")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> MemberId.of(0L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MemberId.of(-1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T-6. 동일 value면 equals/hashCode 일치")
    void equality() {
        assertThat(MemberId.of(1L)).isEqualTo(MemberId.of(1L));
        assertThat(MemberId.of(1L)).isNotEqualTo(MemberId.of(2L));
        assertThat(MemberId.of(1L).hashCode()).isEqualTo(MemberId.of(1L).hashCode());
    }
}
