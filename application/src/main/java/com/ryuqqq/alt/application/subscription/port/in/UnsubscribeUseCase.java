package com.ryuqqq.alt.application.subscription.port.in;

import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;

/**
 * 해지 UseCase (Port-In).
 * 회원과 채널이 모두 존재해야 하며, 채널이 해지 가능 타입이어야 한다.
 */
public interface UnsubscribeUseCase {

    UnsubscribeResult execute(UnsubscribeCommand command);
}
