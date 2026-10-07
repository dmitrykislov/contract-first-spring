package io.github.dmitrykislov.contractfirst.server;

import java.util.Optional;
import org.springframework.http.HttpStatusCode;

/**
 * Tells {@link ContractExceptionHandler} which HTTP status a domain exception maps to. Register one
 * bean per API; mappers are consulted in {@code @Order}, first non-empty answer wins. The exception's
 * message becomes the problem's {@code detail}, the status's reason phrase its {@code title}.
 *
 * <pre>
 * &#64;Component
 * class OrdersDomainExceptionMapper implements DomainExceptionMapper {
 *     public Optional&lt;HttpStatusCode&gt; statusOf(Throwable ex) {
 *         return switch (ex) {
 *             case OrdersDomainException.OrderNotFound _ -> Optional.of(HttpStatus.NOT_FOUND);
 *             ...
 *             default -> Optional.empty();
 *         };
 *     }
 * }
 * </pre>
 */
@FunctionalInterface
public interface DomainExceptionMapper {

    Optional<HttpStatusCode> statusOf(Throwable exception);
}
