package io.github.dmitrykislov.orders.e2e;

import io.github.dmitrykislov.orders.client.auth.OrdersTokenProvider;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * A consumer in {@code provider} mode: it supplies its own {@link OrdersTokenProvider} that obtains
 * the token from somewhere else at call time (here a fake vault; in practice an OAuth token endpoint
 * or a secret manager, usually with caching).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TokenFetchingClientApplication {

    public static final class FakeVault {
        private final String token;
        private final AtomicInteger fetches = new AtomicInteger();

        FakeVault(String token) {
            this.token = token;
        }

        String fetchToken() {
            fetches.incrementAndGet();
            return token;
        }

        public int fetches() {
            return fetches.get();
        }
    }

    @Bean
    FakeVault fakeVault(@Value("${e2e.vault.token}") String token) {
        return new FakeVault(token);
    }

    @Bean
    OrdersTokenProvider ordersTokenProvider(FakeVault vault) {
        return () -> Optional.of(vault.fetchToken());
    }
}
