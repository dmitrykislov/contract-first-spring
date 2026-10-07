package io.github.dmitrykislov.examples.orders.client;

import io.github.dmitrykislov.contractfirst.testing.junit.AbstractClientContractCoverageTest;
import io.github.dmitrykislov.contractfirst.testing.Contract;
import io.github.dmitrykislov.examples.orders.client.api.OrdersApi;
import io.github.dmitrykislov.examples.orders.spec.OrdersContract;

/** Every operation in the contract is exactly one @HttpExchange method on a generated interface. */
class ClientContractCoverageTest extends AbstractClientContractCoverageTest {

    @Override
    protected Contract contract() {
        return Contract.fromClasspath(OrdersContract.RESOURCE, OrdersContract.BASE_PATH);
    }

    @Override
    protected String apiPackage() {
        return OrdersApi.class.getPackageName();
    }
}
