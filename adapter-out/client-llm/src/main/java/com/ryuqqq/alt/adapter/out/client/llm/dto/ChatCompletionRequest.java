package com.ryuqqq.alt.adapter.out.client.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI Chat Completion API 요청 DTO.
 * https://platform.openai.com/docs/api-reference/chat/create
 *
 * snake_case 필드는 @JsonProperty 로 명시.
 * response_format 은 nullable — null 이면 직렬화에서 제외.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
    String model,
    List<Message> messages,
    @JsonProperty("max_tokens") Integer maxTokens,
    Double temperature,
    @JsonProperty("response_format") ResponseFormat responseFormat
) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }

    /**
     * OpenAI 의 JSON 모드. 응답 message.content 가 항상 valid JSON 문자열로 강제됨.
     * 단, 시스템/유저 프롬프트 어딘가에 "JSON" 단어가 포함돼야 함 (OpenAI 정책).
     */
    public record ResponseFormat(String type) {

        public static ResponseFormat jsonObject() {
            return new ResponseFormat("json_object");
        }
    }
}
