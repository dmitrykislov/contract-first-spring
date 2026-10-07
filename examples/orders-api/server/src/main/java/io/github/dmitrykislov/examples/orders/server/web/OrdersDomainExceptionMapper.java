package io.github.dmitrykislov.examples.orders.server.web;

import io.github.dmitrykislov.contractfirst.server.DomainExceptionMapper;
import io.github.dmitrykislov.examples.orders.server.domain.OrdersDomainException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

/**
 * The one piece of error handling that belongs to this API: which status each business failure gets.
 * The sealed hierarchy keeps the switch exhaustive, so a new failure type is a compile error until it
 * has a status. Rendering as {@code ProblemDetail} is done by the shared {@code ContractExceptionHandler}.
 */
@Component
public class OrdersDomainExceptionMapper implements DomainExceptionMapper {

    @Override
    public Optional<HttpStatusCode> statusOf(Throwable exception) {
        if (!(exception instanceof OrdersDomainException domain)) {
            return Optional.empty();
        }
        HttpStatus status = switch (domain) {
            case OrdersDomainException.OrderNotFound _ -> HttpStatus.NOT_FOUND;
            case OrdersDomainException.UnknownSku _ -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OrdersDomainException.MixedCurrencies _ -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OrdersDomainException.IllegalOrderState _ -> HttpStatus.CONFLICT;
            case OrdersDomainException.IdempotencyKeyReused _ -> HttpStatus.CONFLICT;
            case OrdersDomainException.VersionMismatch _ -> HttpStatus.PRECONDITION_FAILED;
        };
        return Optional.of(status);
    }
}
