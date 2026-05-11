package com.ryuqqq.alt.adapter.out.client.llm.exception;

public class LlmServerException extends LlmClientException {

    public LlmServerException(int httpStatus, String message) {
        super("llm server error: " + message, httpStatus);
    }
}
