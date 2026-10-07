package io.github.dmitrykislov.contractfirst.client.auth;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Explicitly scopes a caller token to a block of code for {@code propagate} mode, independent of any
 * web request. Built on {@link ScopedValue}, so it is inherited by virtual threads and structured
 * concurrency scopes without the leaks of a {@code ThreadLocal}.
 *
 * <pre>
 * TokenContext.with(callerToken, () -> ordersApi.getOrder(id, null));
 * </pre>
 */
public final class TokenContext {

    private static final ScopedValue<String> TOKEN = ScopedValue.newInstance();

    private TokenContext() {}

    public static <T> T with(String token, Supplier<T> action) {
        return ScopedValue.where(TOKEN, token).call(action::get);
    }

    public static void run(String token, Runnable action) {
        ScopedValue.where(TOKEN, token).run(action);
    }

    public static Optional<String> current() {
        return TOKEN.isBound() ? Optional.of(TOKEN.get()) : Optional.empty();
    }
}
