package com.ryuqqq.alt.domain.member;

import com.ryuqqq.alt.domain.error.InvalidSubscribeTransitionException;
import com.ryuqqq.alt.domain.error.InvalidUnsubscribeTransitionException;

import java.util.Objects;


/**
 * 회원 Aggregate Root.
 *
 * - 정적 팩토리 forNew(신규) / reconstitute(영속 복원)만 노출 (DOM-AGG-001)
 * - 상태 변경은 비즈니스 메서드 applySubscribe / applyUnsubscribe 만 (DOM-AGG-004 — Setter 금지)
 * - LoD 준수를 위해 canSubscribeTo / canUnsubscribeTo 를 직접 노출
 * - equals/hashCode는 ID 기반 (DOM-AGG-010)
 */
public final class Member {

    private final MemberId id;
    private final PhoneNumber phoneNumber;
    private SubscriptionStatus status;

    private Member(MemberId id, PhoneNumber phoneNumber, SubscriptionStatus status) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.status = status;
    }

    public static Member forNew(PhoneNumber phoneNumber, SubscriptionStatus initial) {
        return new Member(MemberId.forNew(), phoneNumber, initial);
    }

    public static Member reconstitute(MemberId id, PhoneNumber phoneNumber, SubscriptionStatus status) {
        return new Member(id, phoneNumber, status);
    }

    /**
     * 영속화 직후 DB 가 채번한 ID 를 부여하는 용도.
     * 기존 필드(phoneNumber, status)는 보존하고 ID 만 갈아끼운 새 인스턴스를 반환한다.
     */
    public Member withId(MemberId id) {
        return new Member(id, this.phoneNumber, this.status);
    }

    public boolean canSubscribeTo(SubscriptionStatus target) {
        return status.canSubscribeTo(target);
    }

    public boolean canUnsubscribeTo(SubscriptionStatus target) {
        return status.canUnsubscribeTo(target);
    }

    public void applySubscribe(SubscriptionStatus target) {
        if (!status.canSubscribeTo(target)) {
            throw new InvalidSubscribeTransitionException(status + " -> " + target);
        }
        this.status = target;
    }

    public void applyUnsubscribe(SubscriptionStatus target) {
        if (!status.canUnsubscribeTo(target)) {
            throw new InvalidUnsubscribeTransitionException(status + " -> " + target);
        }
        this.status = target;
    }

    public MemberId id() {
        return id;
    }

    public PhoneNumber phoneNumber() {
        return phoneNumber;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public String statusDisplayName() {
        return status.displayName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id != null && !id.isNew() && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * 식별자 + 상태만 노출. 휴대폰 번호(PII)는 의도적으로 제외.
     */
    @Override
    public String toString() {
        return "Member{id=" + (id.isNew() ? "new" : id.value())
            + ", status=" + status.displayName()
            + "}";
    }
}
