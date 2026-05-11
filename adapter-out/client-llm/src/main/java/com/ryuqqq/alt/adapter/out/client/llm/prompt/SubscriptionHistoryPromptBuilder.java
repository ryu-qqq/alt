package com.ryuqqq.alt.adapter.out.client.llm.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqqq.alt.adapter.out.client.llm.dto.ChatCompletionRequest;
import com.ryuqqq.alt.adapter.out.client.llm.exception.LlmParseException;
import com.ryuqqq.alt.application.subscription.dto.SubscriptionHistoryReadBundle;
import com.ryuqqq.alt.domain.channel.Channels;
import com.ryuqqq.alt.domain.subscription.SubscriptionAttempt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * SubscriptionHistoryReadBundle → OpenAI ChatCompletion messages 변환.
 *
 * 입력 매핑은 어댑터 책임 (APP-PRT-003) — Application 은 Bundle 만 넘기고,
 * 어댑터가 도메인 객체에서 외부 API 표현으로 평탄화한다.
 *
 * 출력은 {"summary": "<현재 상태 한 줄>"} — LLM 은 한 줄만 책임.
 * 이력 items 자체는 우리 DB 가 단일 진실. LLM 출력에 free-form Korean string 을 박아 예측 불가능하게 만들지 않는다.
 *
 * 정렬: items 는 시간순(ASC) — 자연어 요약이 "처음 가입한 뒤... 그 다음..." 흐름을 만들기 좋다.
 */
@Component
public class SubscriptionHistoryPromptBuilder {

    private static final String SYSTEM_PROMPT = """
        당신은 구독 서비스 이력의 현재 상태를 한국어 한 줄로 요약하는 어시스턴트입니다.

        응답은 반드시 아래 JSON 스키마로만 답하세요. 코드블록이나 추가 텍스트 절대 금지.
        {
          "status": "NONE" | "BASIC" | "PREMIUM",
          "narrative": "<현재 상태 한 줄 한국어>"
        }

        규칙:
        - status: 마지막 이벤트의 toStatus 에 해당하는 영어 enum 값 셋 중 하나만 (NONE / BASIC / PREMIUM)
          - "구독 안함" → NONE
          - "일반 구독" → BASIC
          - "프리미엄 구독" → PREMIUM
        - narrative: 한 줄 한국어 (50자 이내). 형식 권장: "현재는 [상태] 상태입니다." 또는 자연스러운 변형
        - 입력은 시간순(오래된 → 최신)으로 정렬된 JSON 배열. 마지막 이벤트의 toStatus 가 현재 상태
        - 추측·부연·상세 narrative 절대 금지

        예시 입력:
        [
          {"channelName":"홈페이지","fromStatus":"구독 안함","toStatus":"일반 구독","kind":"구독","occurredAt":"2026-01-01T00:00:00Z"},
          {"channelName":"콜센터","fromStatus":"일반 구독","toStatus":"구독 안함","kind":"해지","occurredAt":"2026-03-01T00:00:00Z"}
        ]
        예시 출력:
        {"status":"NONE","narrative":"현재는 구독 안함 상태입니다."}
        """;

    private final ObjectMapper objectMapper;

    public SubscriptionHistoryPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ChatCompletionRequest.Message> build(SubscriptionHistoryReadBundle bundle) {
        List<HistoryItem> items = toItems(bundle);

        String userContent;
        try {
            userContent = objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new LlmParseException("failed to serialize history items: " + e.getOriginalMessage());
        }

        return List.of(
            ChatCompletionRequest.Message.system(SYSTEM_PROMPT),
            ChatCompletionRequest.Message.user(userContent)
        );
    }

    private List<HistoryItem> toItems(SubscriptionHistoryReadBundle bundle) {
        Channels channels = bundle.channels();
        return bundle.committedAttempts().stream()
            .sorted(Comparator.comparing(SubscriptionAttempt::completedAt))
            .map(attempt -> new HistoryItem(
                channels.nameOf(attempt.channelId()),
                attempt.fromStatusDisplayName(),
                attempt.toStatusDisplayName(),
                attempt.kindDisplayName(),
                attempt.completedAt()
            ))
            .toList();
    }

    /**
     * 어댑터 내부 표현. JSON 키 이름이 system prompt 예시와 일치하도록 fromStatus/toStatus/kind 로 짧게 둔다.
     */
    private record HistoryItem(
        String channelName,
        String fromStatus,
        String toStatus,
        String kind,
        Instant occurredAt
    ) {
    }
}
