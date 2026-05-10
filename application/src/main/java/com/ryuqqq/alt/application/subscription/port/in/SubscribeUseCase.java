package com.ryuqqq.alt.application.subscription.port.in;

import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;

/**
 * 구독 UseCase (Port-In).
 * 회원이 존재하지 않으면 신규 생성 후 구독 시도. csrng 응답에 따라 commit/rollback/fail.
 */
public interface SubscribeUseCase {

    SubscribeResult execute(SubscribeCommand command);
}
