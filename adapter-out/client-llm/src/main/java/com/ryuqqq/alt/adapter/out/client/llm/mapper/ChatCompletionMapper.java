package com.ryuqqq.alt.adapter.out.client.llm.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.alt.adapter.out.client.llm.config.LlmClientProperties;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionResponse;
import com.ryuqqq.alt.adapter.out.client.llm.dto.SummaryPayload;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmParseException;
import com.ryuqqq.alt.adapter.out.client.llm.prompt.SubscriptionHistoryPromptBuilder;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.domain.member.SubscriptionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bundle ↔ ChatCompletion API 양방향 매퍼.
 *
 * - toRequest      : Bundle + properties → ChatCompletionRequest (PromptBuilder 위임)
 * - parsePayload   : ChatCompletionResponse → SummaryPayload (json 역직렬화)
 * - resolveSummary : SummaryPayload → 최종 summary 문자열 (narrative valid 시 그대로, invalid 시 status 템플릿 fallback)
 *
 * 어댑터(LlmClientAdapter) 는 호출/예외 분기만 담당하고, 변환·검증·fallback 은 모두 매퍼가 응집한다.
 */
@Component
public class ChatCompletionMapper {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionMapper.class);
    private static final int NARRATIVE_MAX_LENGTH = 100;

    private final SubscriptionHistoryPromptBuilder promptBuilder;
    private final LlmClientProperties properties;
    private final ObjectMapper objectMapper;

    public ChatCompletionMapper(
        SubscriptionHistoryPromptBuilder promptBuilder,
        LlmClientProperties properties,
        ObjectMapper objectMapper
    ) {
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatCompletionRequest toRequest(SubscriptionHistoryReadBundle bundle) {
        return new ChatCompletionRequest(
            properties.model(),
            promptBuilder.build(bundle),
            properties.maxTokens(),
            properties.temperature(),
            ChatCompletionRequest.ResponseFormat.jsonObject()
        );
    }

    public SummaryPayload parsePayload(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmParseException("empty choices");
        }
        ChatCompletionResponse.Choice first = response.choices().get(0);
        if (first.message() == null || first.message().content() == null) {
            throw new LlmParseException("missing message content");
        }
        String contentJson = first.message().content().trim();
        if (contentJson.isEmpty()) {
            throw new LlmParseException("blank content");
        }
        try {
            return objectMapper.readValue(contentJson, SummaryPayload.class);
        } catch (JsonProcessingException e) {
            throw new LlmParseException("malformed inner JSON: " + e.getOriginalMessage());
        }
    }

    /**
     * 하이브리드 정책:
     * - narrative valid (not blank, length 제한 내) → 그대로
     * - narrative invalid → status 기반 템플릿 ("현재는 [상태] 상태입니다.")
     * - status 마저 invalid → LlmParseException (어댑터 outer catch 가 unavailable 흡수)
     */
    public String resolveSummary(SummaryPayload payload) {
        SubscriptionStatus status = parseStatus(payload.status());
        String narrative = payload.narrative();
        if (narrative != null && !narrative.isBlank() && narrative.length() <= NARRATIVE_MAX_LENGTH) {
            return narrative.trim();
        }
        log.warn("llm narrative invalid — falling back to template (status={})", status);
        return "현재는 " + status.displayName() + " 상태입니다.";
    }

    private SubscriptionStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmParseException("missing status field");
        }
        try {
            return SubscriptionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LlmParseException("unknown status value: " + raw);
        }
    }
}
