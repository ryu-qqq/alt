package com.ryuqqq.alt.adapter.in.subscription.controller;

import com.ryuqqq.alt.adapter.in.common.response.ApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.SubscriptionEndpoints;
import com.ryuqqq.alt.adapter.in.subscription.dto.request.QuerySubscriptionHistoryApiRequest;
import com.ryuqqq.alt.adapter.in.subscription.dto.response.QuerySubscriptionHistoryApiResponse;
import com.ryuqqq.alt.adapter.in.subscription.mapper.SubscriptionApiMapper;
import com.ryuqqq.alt.application.subscription.dto.query.QuerySubscriptionHistoryQuery;
import com.ryuqqq.alt.application.subscription.dto.response.QuerySubscriptionHistoryResult;
import com.ryuqqq.alt.application.subscription.port.in.QuerySubscriptionHistoryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Subscription Query", description = "구독 이력 조회 API")
public class SubscriptionQueryController {

    private final QuerySubscriptionHistoryUseCase querySubscriptionHistoryUseCase;

    public SubscriptionQueryController(QuerySubscriptionHistoryUseCase querySubscriptionHistoryUseCase) {
        this.querySubscriptionHistoryUseCase = querySubscriptionHistoryUseCase;
    }

    @GetMapping(SubscriptionEndpoints.HISTORY)
    @Operation(
        summary = "구독 이력 조회",
        description = "휴대폰 번호 기준 회원의 COMMITTED 이력을 최신순으로 반환한다. "
            + "이력이 1건 이상이면 LLM 자연어 요약을 함께 제공하며, LLM 호출 실패 시 summary 는 null 로 graceful degradation."
    )
    public ResponseEntity<ApiResponse<QuerySubscriptionHistoryApiResponse>> getHistory(
        @Valid QuerySubscriptionHistoryApiRequest request
    ) {
        QuerySubscriptionHistoryQuery query = SubscriptionApiMapper.toQuerySubscriptionHistoryQuery(request);
        QuerySubscriptionHistoryResult result = querySubscriptionHistoryUseCase.execute(query);
        return ResponseEntity.ok(ApiResponse.of(SubscriptionApiMapper.toHistoryResponse(result)));
    }
}
