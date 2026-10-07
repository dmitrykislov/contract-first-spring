package io.github.dmitrykislov.orders.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OrdersServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersServerApplication.class, args);
    }
}
