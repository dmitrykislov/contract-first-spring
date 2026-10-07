package io.github.dmitrykislov.orders.server.domain;

import java.util.Set;
import java.util.UUID;

/**
 * Closed set of business failures the service layer can raise. Being sealed lets the web layer map
 * every case exhaustively with a pattern-matching {@code switch}; adding a new case becomes a
 * compile error until it is mapped to an HTTP status.
 */
public sealed abstract class OrdersDomainException extends RuntimeException {

    protected OrdersDomainException(String message) {
        super(message);
    }

    /** No order exists with the given id. */
    public static final class OrderNotFound extends OrdersDomainException {
        private final UUID orderId;

        public OrderNotFound(UUID orderId) {
            super("Order %s does not exist".formatted(orderId));
            this.orderId = orderId;
        }

        public UUID orderId() {
            return orderId;
        }
    }

    /** No product exists with the given SKU. */
    public static final class UnknownSku extends OrdersDomainException {
        private final String sku;

        public UnknownSku(String sku) {
            super("Unknown SKU '%s'".formatted(sku));
            this.sku = sku;
        }

        public String sku() {
            return sku;
        }
    }

    /** Order lines are priced in more than one currency. */
    public static final class MixedCurrencies extends OrdersDomainException {
        public MixedCurrencies(Set<String> currencies) {
            super("All lines must share one currency, found %s".formatted(currencies));
        }
    }

    /** The order is not in a status that permits the requested transition or modification. */
    public static final class IllegalOrderState extends OrdersDomainException {
        public IllegalOrderState(UUID orderId, OrderState current, String attempted) {
            super("Order %s is %s and cannot be %s".formatted(orderId, current, attempted));
        }
    }

    /** The idempotency key was already used for a creation request with a different payload. */
    public static final class IdempotencyKeyReused extends OrdersDomainException {
        public IdempotencyKeyReused(String key) {
            super("Idempotency-Key '%s' was already used with a different payload".formatted(key));
        }
    }

    /** An {@code If-Match} precondition did not match the current version. */
    public static final class VersionMismatch extends OrdersDomainException {
        public VersionMismatch(UUID orderId, long expected, long actual) {
            super("Order %s is at version %d, not %d".formatted(orderId, actual, expected));
        }
    }
}
