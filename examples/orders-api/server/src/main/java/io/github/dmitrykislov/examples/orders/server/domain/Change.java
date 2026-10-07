package io.github.dmitrykislov.examples.orders.server.domain;

import org.jspecify.annotations.Nullable;

/**
 * The three things a partial update can say about one field (JSON Merge Patch, RFC 7396): leave it
 * alone, replace it, or clear it. Makes the distinction explicit instead of overloading {@code null}.
 */
public sealed interface Change<T> {

    @Nullable T applyTo(@Nullable T current);

    @SuppressWarnings("unchecked")
    static <T> Change<T> keep() {
        return (Change<T>) Keep.INSTANCE;
    }

    static <T> Change<T> set(T value) {
        return new Set<>(value);
    }

    @SuppressWarnings("unchecked")
    static <T> Change<T> clear() {
        return (Change<T>) Clear.INSTANCE;
    }

    /** The field was not mentioned. */
    final class Keep<T> implements Change<T> {
        private static final Keep<?> INSTANCE = new Keep<>();

        @Override
        public @Nullable T applyTo(@Nullable T current) {
            return current;
        }
    }

    /** The field gets a new value. */
    record Set<T>(T value) implements Change<T> {
        @Override
        public T applyTo(@Nullable T current) {
            return value;
        }
    }

    /** The field was explicitly set to {@code null}. */
    final class Clear<T> implements Change<T> {
        private static final Clear<?> INSTANCE = new Clear<>();

        @Override
        public @Nullable T applyTo(@Nullable T current) {
            return null;
        }
    }
}
