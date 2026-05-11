package com.ryuqqq.alt.adapter.out.client.llm.exception;

public class LlmNetworkException extends LlmClientException {

    public LlmNetworkException(String message, Throwable cause) {
        super("llm network error: " + message, -1, cause);
    }
}
