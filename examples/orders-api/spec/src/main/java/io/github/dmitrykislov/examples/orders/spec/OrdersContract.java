package io.github.dmitrykislov.examples.orders.spec;

/**
 * Where the Orders contract lives and how it is mounted. Constants only, so that every module (client,
 * server, tests) refers to the same resource and base path without depending on anything heavier.
 */
public final class OrdersContract {

    /** Classpath location of the OpenAPI document packaged by this module. */
    public static final String RESOURCE = "openapi/orders-api.yaml";

    /** Path part of {@code servers[0].url}; every operation path is relative to it. */
    public static final String BASE_PATH = "/api/v1";

    private OrdersContract() {}
}
