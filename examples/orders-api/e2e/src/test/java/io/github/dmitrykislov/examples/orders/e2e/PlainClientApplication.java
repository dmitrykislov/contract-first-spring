package io.github.dmitrykislov.examples.orders.e2e;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * A consumer without client-side contract validation, used to prove how the server answers requests
 * that a less careful client would send.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class PlainClientApplication {}
