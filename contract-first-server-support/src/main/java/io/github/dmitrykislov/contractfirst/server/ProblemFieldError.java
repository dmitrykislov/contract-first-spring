package io.github.dmitrykislov.contractfirst.server;

import org.jspecify.annotations.Nullable;

/**
 * One entry of the {@code errors} extension on validation problems. Serialises to the same JSON shape
 * as the {@code FieldError} schema the contracts in this toolkit use.
 */
public record ProblemFieldError(String field, String message, @Nullable String rejectedValue) {}
