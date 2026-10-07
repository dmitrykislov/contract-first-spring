package io.github.dmitrykislov.orders.testsupport;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import java.net.URL;
import java.util.Objects;

/** Locates the Orders API contract on the classpath and builds validators for it. */
public final class OrdersContract {

    public static final String SPEC_RESOURCE = "openapi/orders-api.yaml";

    /** Path prefix from {@code servers[0].url}; requests are matched against the contract below it. */
    public static final String BASE_PATH = "/api/v1";

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator
            .createForSpecificationUrl(specUrl().toString())
            .withBasePathOverride(BASE_PATH)
            .build();

    private OrdersContract() {}

    public static OpenApiInteractionValidator validator() {
        return VALIDATOR;
    }

    public static URL specUrl() {
        return Objects.requireNonNull(OrdersContract.class.getClassLoader().getResource(SPEC_RESOURCE),
                "contract " + SPEC_RESOURCE + " is missing from the classpath (add orders-api-spec)");
    }
}
