package io.github.dmitrykislov.orders.client.auth;

/** Raised before a request is sent when no token can be obtained for it. */
public class OrdersClientAuthenticationException extends RuntimeException {

    public OrdersClientAuthenticationException(String message) {
        super(message);
    }
}
