package com.ryuqqq.alt.adapter.in.subscription.controller;

import com.ryuqqq.alt.adapter.in.common.response.ApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.SubscriptionEndpoints;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.SubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.UnsubscribeApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.SubscribeApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.UnsubscribeApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.mapper.SubscriptionApiMapper;
import com.ryuqqq.alt.application.subscription.dto.command.SubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.command.UnsubscribeCommand;
import com.ryuqqq.alt.application.subscription.dto.response.SubscribeResult;
import com.ryuqqq.alt.application.subscription.dto.response.UnsubscribeResult;
import com.ryuqqq.alt.application.subscription.port.in.SubscribeUseCase;
import com.ryuqqq.alt.application.subscription.port.in.UnsubscribeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Subscription Command", description = "구독 / 해지 명령 API")
public class SubscriptionCommandController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final SubscribeUseCase subscribeUseCase;
    private final UnsubscribeUseCase unsubscribeUseCase;

    public SubscriptionCommandController(
        SubscribeUseCase subscribeUseCase,
        UnsubscribeUseCase unsubscribeUseCase
    ) {
        this.subscribeUseCase = subscribeUseCase;
        this.unsubscribeUseCase = unsubscribeUseCase;
    }

    @PostMapping(SubscriptionEndpoints.SUBSCRIBE)
    @Operation(
        summary = "구독 신청",
        description = "외부 API 응답에 따라 트랜잭션 커밋(random=1) / 롤백(random=0) / 실패(외부 장애) 처리. "
            + "Idempotency-Key 헤더 필수 — 같은 키 재요청은 HTTP 409 로 거절 (외부 호출 차단)."
    )
    public ResponseEntity<ApiResponse<SubscribeApiResponse>> subscribe(
        @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
        @Valid @RequestBody SubscribeApiRequest request
    ) {
        SubscribeCommand command = SubscriptionApiMapper.toSubscribeCommand(request, idempotencyKey);
        SubscribeResult result = subscribeUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.of(SubscriptionApiMapper.toSubscribeResponse(result)));
    }

    @PostMapping(SubscriptionEndpoints.UNSUBSCRIBE)
    @Operation(
        summary = "구독 해지",
        description = "기존 회원만 해지 가능. 채널이 해지 가능 타입이어야 하며, 도메인 정책상 허용되는 전이만 처리. "
            + "외부 API 응답에 따라 커밋 / 롤백 / 실패 처리. "
            + "Idempotency-Key 헤더 필수 — 같은 키 재요청은 HTTP 409 로 거절."
    )
    public ResponseEntity<ApiResponse<UnsubscribeApiResponse>> unsubscribe(
        @RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
        @Valid @RequestBody UnsubscribeApiRequest request
    ) {
        UnsubscribeCommand command = SubscriptionApiMapper.toUnsubscribeCommand(request, idempotencyKey);
        UnsubscribeResult result = unsubscribeUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.of(SubscriptionApiMapper.toUnsubscribeResponse(result)));
    }
}
