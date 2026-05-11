package com.ryuqqq.alt.adapter.out.client.llm.adapter;

import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.application.subscription.dto.response.LlmSummaryOutcome;
import com.ryuqqq.alt.application.subscription.port.out.LlmSummaryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * api-key 미설정 환경(로컬·테스트) 에서 부팅을 보장하기 위한 NoOp 어댑터.
 * 항상 LlmSummaryOutcome.unavailable 을 반환하며, 호출자(Service / Assembler) 가 graceful 처리한다.
 *
 * 진짜 어댑터(LlmClientAdapter) 와 ConditionalOnExpression 으로 상호 배타 등록.
 */
@Component
@ConditionalOnExpression("'${llm-client.api-key:}' == ''")
public class NoOpLlmClient implements LlmSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpLlmClient.class);

    public NoOpLlmClient() {
        log.warn("OPENAI_API_KEY 미설정 — NoOpLlmClient 로 부팅. LLM 요약은 항상 unavailable 로 응답합니다.");
    }

    @Override
    public LlmSummaryOutcome summarize(SubscriptionHistoryReadBundle bundle) {
        return LlmSummaryOutcome.unavailable("api key not configured");
    }
}
