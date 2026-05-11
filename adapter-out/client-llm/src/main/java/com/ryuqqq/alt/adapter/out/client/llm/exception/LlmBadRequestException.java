package com.ryuqqq.alt.adapter.out.client.llm.exception;

public class LlmBadRequestException extends LlmClientException {

    public LlmBadRequestException(int httpStatus, String message) {
        super("llm bad request: " + message, httpStatus);
    }
}
