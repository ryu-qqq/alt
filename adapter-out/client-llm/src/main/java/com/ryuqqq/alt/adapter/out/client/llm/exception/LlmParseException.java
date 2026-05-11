package com.ryuqqq.alt.adapter.out.client.llm.exception;

public class LlmParseException extends LlmClientException {

    public LlmParseException(String message) {
        super("llm parse error: " + message, -1);
    }
}
