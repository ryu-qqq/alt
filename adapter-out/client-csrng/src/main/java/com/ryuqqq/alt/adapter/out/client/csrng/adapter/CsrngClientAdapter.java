package com.ryuqqq.alt.adapter.out.client.csrng.adapter;

import com.ryuqqq.alt.adapter.out.client.csrng.dto.CsrngResponse;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngBadRequestException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngNetworkException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngParseException;
import com.ryuqqq.alt.adapter.out.client.csrng.exception.CsrngServerException;
import com.ryuqqq.alt.adapter.out.client.csrng.executor.CsrngApiExecutor;
import com.ryuqqq.alt.application.subscription.dto.response.ExternalCallResult;
import com.ryuqqq.alt.application.subscription.exception.RandomClientException;
import com.ryuqqq.alt.application.subscription.port.out.RandomClient;
import com.ryuqqq.alt.domain.subscription.AttemptFailureReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * RandomClient 구현체. csrng 외부 서비스 호출.
 *
 * 외층 try/catch 는 application 컨트랙트(RandomClientException + AttemptFailureReason)로 번역,
 * 내층 try/catch 는 HTTP 오류를 어댑터 내부 분류 예외(CsrngXxxException)로 변환.
 *
 * Resilience4j CB+Retry 는 CsrngApiExecutor 안에서 적용.
 */
@Component
public class CsrngClientAdapter implements RandomClient {

    private static final Logger log = LoggerFactory.getLogger(CsrngClientAdapter.class);

    private static final int MIN = 0;
    private static final int MAX = 1;

    private final RestClient csrngRestClient;
    private final CsrngApiExecutor csrngApiExecutor;

    public CsrngClientAdapter(RestClient csrngRestClient, CsrngApiExecutor csrngApiExecutor) {
        this.csrngRestClient = csrngRestClient;
        this.csrngApiExecutor = csrngApiExecutor;
    }

    @Override
    public ExternalCallResult call() {
        try {
            return csrngApiExecutor.execute(this::doCall);
        } catch (RandomClientException e) {
            // Executor 가 CB OPEN 을 이미 RandomClientException 으로 번역한 케이스
            throw e;
        } catch (CsrngServerException e) {
            log.warn("csrng server error: status={}", e.httpStatus());
            throw new RandomClientException(AttemptFailureReason.EXTERNAL_SERVER_ERROR, "http " + e.httpStatus());
        } catch (CsrngBadRequestException e) {
            log.warn("csrng bad request: status={}", e.httpStatus());
            throw new RandomClientException(AttemptFailureReason.EXTERNAL_CLIENT_ERROR, "http " + e.httpStatus());
        } catch (CsrngNetworkException e) {
            log.warn("csrng network error", e);
            throw new RandomClientException(AttemptFailureReason.EXTERNAL_TIMEOUT, e.getMessage());
        } catch (CsrngParseException e) {
            log.warn("csrng parse error: {}", e.getMessage());
            throw new RandomClientException(AttemptFailureReason.EXTERNAL_PARSE_FAILURE, e.getMessage());
        } catch (Exception e) {
            log.error("csrng unexpected error", e);
            throw new RandomClientException(AttemptFailureReason.EXTERNAL_UNKNOWN, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ExternalCallResult doCall() {
        CsrngResponse[] responses;
        try {
            responses = csrngRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .queryParam("min", MIN)
                    .queryParam("max", MAX)
                    .build())
                .retrieve()
                .body(CsrngResponse[].class);
        } catch (HttpServerErrorException e) {
            throw new CsrngServerException(e.getStatusCode().value(), e.getMessage());
        } catch (HttpClientErrorException e) {
            throw new CsrngBadRequestException(e.getStatusCode().value(), e.getMessage());
        } catch (ResourceAccessException e) {
            throw new CsrngNetworkException(e.getMessage(), e);
        }

        if (responses == null || responses.length == 0) {
            throw new CsrngParseException("empty response array");
        }
        CsrngResponse first = responses[0];
        if (!"success".equalsIgnoreCase(first.status())) {
            throw new CsrngParseException("status != success : " + first.status());
        }
        int random = first.random();
        if (random == 1) {
            return ExternalCallResult.APPROVED;
        }
        if (random == 0) {
            return ExternalCallResult.REJECTED;
        }
        throw new CsrngParseException("unexpected random=" + random);
    }
}
