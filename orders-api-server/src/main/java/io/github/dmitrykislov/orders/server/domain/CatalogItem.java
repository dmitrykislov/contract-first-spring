package io.github.dmitrykislov.orders.server.domain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A catalog entry. Names are keyed by BCP 47 language tag; {@code en} is always present. */
public record CatalogItem(String sku, Map<String, String> names, StoredOrder.Amount price, boolean inStock) {

    private static final String DEFAULT_LANGUAGE = "en";

    /** Picks the best name for an {@code Accept-Language} value (RFC 4647 lookup), falling back to English. */
    public String nameFor(@Nullable String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return names.get(DEFAULT_LANGUAGE);
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
            String match = Locale.lookupTag(ranges, names.keySet());
            return match != null ? names.get(match) : names.get(DEFAULT_LANGUAGE);
        } catch (IllegalArgumentException malformedHeader) {
            return names.get(DEFAULT_LANGUAGE);
        }
    }
}
