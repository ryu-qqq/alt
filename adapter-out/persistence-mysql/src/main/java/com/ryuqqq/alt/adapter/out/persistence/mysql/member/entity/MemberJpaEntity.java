package com.ryuqqq.alt.adapter.out.persistence.mysql.member.entity;

import com.ryuqqq.alt.adapter.out.persistence.mysql.common.BaseAuditEntity;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class MemberJpaEntity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 11)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    protected MemberJpaEntity() {
    }

    private MemberJpaEntity(Long id, String phoneNumber, SubscriptionStatus status) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.status = status;
    }

    public static MemberJpaEntity create(Long id, String phoneNumber, SubscriptionStatus status) {
        return new MemberJpaEntity(id, phoneNumber, status);
    }

    public Long getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}
