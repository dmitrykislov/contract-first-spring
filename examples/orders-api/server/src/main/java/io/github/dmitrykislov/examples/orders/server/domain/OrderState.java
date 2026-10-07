package io.github.dmitrykislov.examples.orders.server.domain;

/** Lifecycle of an order; mirrors {@code OrderStatus} in the contract but belongs to the domain. */
public enum OrderState {
    PENDING,
    SUBMITTED,
    SHIPPED,
    CANCELLED;

    public boolean isMutable() {
        return this == PENDING;
    }

    public boolean isCancellable() {
        return this == PENDING || this == SUBMITTED;
    }
}
