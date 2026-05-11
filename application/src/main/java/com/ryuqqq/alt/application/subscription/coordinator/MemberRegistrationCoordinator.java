package com.ryuqqq.alt.application.subscription.coordinator;

import com.ryuqqq.alt.application.subscription.port.out.MemberCommandPort;
import com.ryuqqq.alt.application.subscription.port.out.MemberQueryPort;
import com.ryuqqq.alt.domain.member.Member;
import com.ryuqqq.alt.domain.member.MemberId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멤버 등록 코디네이터.
 *
 * 휴대폰 번호로 기존 회원을 조회하고, 없으면 신규 회원을 영속화한 뒤 반환한다.
 * 외부 API 호출과 무관한 prep 단계로, 자체 트랜잭션 안에서 atomic 하게 commit 된다.
 * 상위 호출자(Service) 는 트랜잭션을 가지지 않으므로 자연스럽게 본 메서드의 commit 이
 * 외부 호출 단계와 분리된다 (ADR-0002 사가 정책).
 */
@Component
public class MemberRegistrationCoordinator {

    private final MemberQueryPort memberQueryPort;
    private final MemberCommandPort memberCommandPort;

    public MemberRegistrationCoordinator(
        MemberQueryPort memberQueryPort,
        MemberCommandPort memberCommandPort
    ) {
        this.memberQueryPort = memberQueryPort;
        this.memberCommandPort = memberCommandPort;
    }

    @Transactional
    public Member findOrRegister(Member newMember) {
        return memberQueryPort.findByPhoneNumber(newMember.phoneNumber())
            .orElseGet(() -> newMember.withId(MemberId.of(memberCommandPort.persist(newMember))));
    }
}
