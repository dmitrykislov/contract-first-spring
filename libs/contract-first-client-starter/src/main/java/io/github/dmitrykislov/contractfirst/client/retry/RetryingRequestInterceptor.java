package io.github.dmitrykislov.contractfirst.client.retry;

import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Retries idempotent requests on connection failures and transient statuses with exponential
 * backoff (see {@link RetryProperties}). When attempts are exhausted the last response is returned
 * unchanged, so the normal status handler still turns it into the API's typed exception; the last
 * {@link IOException} is rethrown. Must be the outermost interceptor so that token resolution and
 * request-id propagation run again on every attempt.
 */
public final class RetryingRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryingRequestInterceptor.class);
    private static final Set<HttpMethod> ALWAYS_IDEMPOTENT = Set.of(
            HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.PUT, HttpMethod.DELETE);

    private final RetryProperties retry;
    private final Sleeper sleeper;

    public RetryingRequestInterceptor(RetryProperties retry) {
        this(retry, Thread::sleep);
    }

    RetryingRequestInterceptor(RetryProperties retry, Sleeper sleeper) {
        this.retry = retry;
        this.sleeper = sleeper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!retry.active() || !isIdempotent(request)) {
            return execution.execute(request, body);
        }
        BackOffExecution backOff = backOff().start();
        for (int attempt = 1; ; attempt++) {
            boolean last = attempt >= retry.maxAttempts();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (last || !retry.retryableStatuses().contains(response.getStatusCode().value())) {
                    return response;
                }
                log.debug("{} {} answered {} on attempt {}/{}, retrying", request.getMethod(), request.getURI(),
                        response.getStatusCode().value(), attempt, retry.maxAttempts());
                response.close();
            } catch (IOException e) {
                if (last) {
                    throw e;
                }
                log.debug("{} {} failed on attempt {}/{}: {}, retrying", request.getMethod(), request.getURI(),
                        attempt, retry.maxAttempts(), e.toString());
            }
            pause(backOff.nextBackOff());
        }
    }

    private boolean isIdempotent(HttpRequest request) {
        return ALWAYS_IDEMPOTENT.contains(request.getMethod())
                || request.getHeaders().containsHeader(retry.idempotencyHeader());
    }

    private ExponentialBackOff backOff() {
        ExponentialBackOff backOff = new ExponentialBackOff(retry.initialBackoff().toMillis(), retry.multiplier());
        backOff.setMaxInterval(retry.maxBackoff().toMillis());
        return backOff;
    }

    private void pause(long millis) throws IOException {
        if (millis == BackOffExecution.STOP || millis <= 0) {
            return;
        }
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry", interrupted);
        }
    }

    /** Seam for tests; production sleeps the thread. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
