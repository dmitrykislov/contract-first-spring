package io.github.dmitrykislov.contractfirst.client.errors;

import org.jspecify.annotations.Nullable;

/**
 * One entry of a problem's {@code errors} extension, the convention this toolkit uses for field-level
 * validation failures: {@code [{"field": "lines[0].quantity", "message": "...", "rejectedValue": "0"}]}.
 */
public record ApiFieldError(String field, String message, @Nullable String rejectedValue) {}
