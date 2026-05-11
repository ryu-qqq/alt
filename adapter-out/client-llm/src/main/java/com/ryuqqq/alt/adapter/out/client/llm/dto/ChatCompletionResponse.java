package com.ryuqqq.alt.adapter.out.client.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Chat Completion API 응답 DTO.
 *
 * 우리는 choices[0].message.content 만 사용.
 * 그 외 필드 (usage, created, object 등) 는 Spring Boot Jackson 기본 설정상 ignore.
 */
public record ChatCompletionResponse(
    String id,
    String model,
    List<Choice> choices
) {

    public record Choice(
        int index,
        Message message,
        @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Message(String role, String content) {
    }
}
