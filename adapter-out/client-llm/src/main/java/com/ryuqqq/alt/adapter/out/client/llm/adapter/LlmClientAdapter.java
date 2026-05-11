package com.ryuqqq.alt.adapter.out.client.llm.adapter;

import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionResponse;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmBadRequestException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmNetworkException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmRateLimitException;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmServerException;
import com.ryuqqq.alt.adapter.out.client.llm.executor.LlmApiExecutor;
import com.ryuqqq.alt.adapter.out.client.llm.mapper.ChatCompletionMapper;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * LlmSummaryClient 구현체. OpenAI Chat Completion API 호출.
 *
 * 책임 분리:
 * - 본 클래스 : RestClient 호출 + HTTP 예외 분류 + outer try/catch 로 모든 실패를 unavailable 로 흡수
 * - 변환/파싱/fallback : ChatCompletionMapper 가 응집
 *
 * 등록 조건: api-key 가 비어있지 않을 때만. 빈 환경에서는 NoOpLlmClient 가 대신 등록된다.
 */
@Component
@ConditionalOnExpression("'${llm-client.api-key:}' != ''")
public class LlmClientAdapter implements LlmSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientAdapter.class);

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestClient llmRestClient;
    private final LlmApiExecutor llmApiExecutor;
    private final ChatCompletionMapper mapper;

    public LlmClientAdapter(
        RestClient llmRestClient,
        LlmApiExecutor llmApiExecutor,
        ChatCompletionMapper mapper
    ) {
        this.llmRestClient = llmRestClient;
        this.llmApiExecutor = llmApiExecutor;
        this.mapper = mapper;
    }

    @Override
    public LlmSummaryOutcome summarize(SubscriptionHistoryReadBundle bundle) {
        try {
            ChatCompletionRequest request = mapper.toRequest(bundle);
            ChatCompletionResponse response = llmApiExecutor.execute(() -> doCall(request));
            return LlmSummaryOutcome.success(mapper.resolveSummary(mapper.parsePayload(response)));
        } catch (Exception e) {
            log.warn("llm summary unavailable: {}", e.getMessage());
            return LlmSummaryOutcome.unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ChatCompletionResponse doCall(ChatCompletionRequest request) {
        try {
            return llmRestClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new LlmRateLimitException(e.getMessage());
            }
            throw new LlmBadRequestException(e.getStatusCode().value(), e.getMessage());
        } catch (HttpServerErrorException e) {
            throw new LlmServerException(e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            throw new LlmNetworkException(e.getMessage(), e);
        }
    }
}
