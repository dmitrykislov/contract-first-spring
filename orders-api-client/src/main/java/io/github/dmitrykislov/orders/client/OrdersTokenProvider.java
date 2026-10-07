package io.github.dmitrykislov.orders.client;

import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;

/**
 * The token source for the Orders client. A dedicated subtype of {@link TokenProvider} so that an
 * application using several contract clients can define one provider bean per API without ambiguity:
 *
 * <pre>
 * &#64;Bean OrdersTokenProvider ordersTokenProvider(MyTokenService tokens) { return () -> Optional.of(tokens.current()); }
 * </pre>
 */
@FunctionalInterface
public interface OrdersTokenProvider extends TokenProvider {}
