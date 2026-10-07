# Authentication with the generated client

The Orders contract declares one security scheme, an API key in the `X-API-Key` header, and the generated
`@HttpExchange` interfaces know nothing about it: security requirements are not parameters, so the generator leaves
them out. The shared runtime in `contract-first-client-starter` adds a per-request interceptor that resolves a token
through a `TokenProvider` and writes it to the configured header. The Orders client narrows that to its own marker
type `OrdersTokenProvider` so an application with several contract clients can define one provider bean per API.
Everything below is about choosing and shaping that provider.

Classes referenced below live in `io.github.dmitrykislov.contractfirst.client.auth` (`TokenProvider`,
`TokenContext`, `CachingTokenProvider`, `ClientAuthenticationException`) and `io.github.dmitrykislov.examples.orders.client`
(`OrdersTokenProvider`, `OrdersApiException`).

Settings live under `contract-first.clients.orders.auth` (and `contract-first.clients.defaults.auth` for values shared by every client):

| Property | Default | Meaning |
|----------|---------|---------|
| `mode` | `static` | `static`, `propagate` or `provider` |
| `token` | | the token, `static` mode only |
| `header-name` | `X-API-Key` | request header that carries the token |
| `scheme` | *(empty)* | optional scheme written before the token, e.g. `Bearer` |

Any mode can be overridden by defining your own `OrdersTokenProvider` bean (the Orders client names that marker type
in its `@EnableContractClient`; a client without a marker type looks for a `TokenProvider` bean named
`<group>TokenProvider`). If no token can be resolved, or the provider throws, the call fails before anything is sent
with `ClientAuthenticationException` naming the mode and the remedy.

---

## 1. Static API key (default)

One identity per deployment, read from the environment.

```yaml
spring:
  http:
    serviceclient:
      orders:
        base-url: https://orders.example.com/api/v1
contract-first:
  clients:
    orders:
      auth:
        token: ${ORDERS_API_KEY}
```

```java
@Service
class Checkout {
    private final OrdersApi orders;
    Checkout(OrdersApi orders) { this.orders = orders; }

    Order place(CreateOrderRequest request) {
        return orders.createOrder("idem-" + UUID.randomUUID(), request, null).getBody();
    }
}
```

Keep the secret out of files: pass `ORDERS_API_KEY` as an environment variable, or on Kubernetes mount the secret
as a file and import it as a config tree, which maps `/run/secrets/contract-first.clients.orders.auth.token` to the property:

```yaml
spring:
  config:
    import: optional:configtree:/run/secrets/
```

## 2. Static bearer token on `Authorization`

Same mode, different header. The client inserts the space between scheme and token.

```yaml
contract-first:
  clients:
    orders:
      auth:
        header-name: Authorization
        scheme: Bearer
        token: ${ORDERS_BEARER_TOKEN}
```

Sends `Authorization: Bearer <token>`.

## 3. Propagate the caller's token from the incoming HTTP request

A gateway or BFF that calls Orders on behalf of whoever called it. No token in configuration; the client reads the
same header from the servlet request being handled on the current thread.

```yaml
contract-first:
  clients:
    orders:
      auth:
        mode: propagate
```

```java
@RestController
class CheckoutController {
    private final OrdersApi orders;

    @PostMapping("/checkout")
    Order checkout(@RequestBody CreateOrderRequest request) {
        // The X-API-Key the caller sent to /checkout is forwarded to Orders automatically.
        return orders.createOrder("idem-" + UUID.randomUUID(), request, null).getBody();
    }
}
```

If the incoming header carries a scheme, configure the same scheme so it is stripped on the way in and re-added on
the way out instead of being doubled:

```yaml
contract-first:
  clients:
    orders:
      auth:
        mode: propagate
        header-name: Authorization
        scheme: Bearer      # incoming "Bearer abc" or "bearer abc" -> outgoing "Bearer abc"
```

The incoming header name is always the configured `header-name`. If your inbound and outbound headers differ, use a
custom provider (example 9).

## 4. Propagate an explicit token with `TokenContext`

Outside a servlet request (batch jobs, message listeners, scheduled tasks, tests) or when the caller's token comes
from somewhere other than a header, bind it explicitly. `TokenContext` is a `ScopedValue`, so it is visible to the
whole call tree including virtual threads forked inside the scope, and it unbinds automatically.

```java
// returns a value
Order order = TokenContext.with(callerToken, () -> orders.getOrder(id, null).getBody());

// no return value
TokenContext.run(callerToken, () -> orders.cancelOrder(id, null, "customer request"));
```

A Kafka listener relaying a per-message token:

```java
@KafkaListener(topics = "order-commands")
void onCommand(ConsumerRecord<String, OrderCommand> record) {
    String token = new String(record.headers().lastHeader("x-api-key").value(), UTF_8);
    TokenContext.run(token, () -> orders.submitOrder(record.value().orderId(), null));
}
```

Fan-out with structured concurrency; every subtask inherits the binding:

```java
List<Order> load(List<UUID> ids, String token) {
    return TokenContext.with(token, () -> {
        try (var scope = StructuredTaskScope.open()) {
            var tasks = ids.stream().map(id -> scope.fork(() -> orders.getOrder(id, null).getBody())).toList();
            scope.join();
            return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        }
    });
}
```

`TokenContext` wins over the servlet request when both are present, so a controller can deliberately call Orders as
a different identity for one block.

## 5. Provider mode: fetch the token on the fly

Register your own `OrdersTokenProvider` bean; the client calls it on every request.

```yaml
contract-first:
  clients:
    orders:
      auth:
        mode: provider
```

### 5a. OAuth2 client credentials with Spring Security

Spring Security's `OAuth2AuthorizedClientManager` obtains, caches and refreshes the access token; the provider just
reads it. Add `spring-boot-starter-security-oauth2-client` and a registration:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          orders:
            provider: corp-idp
            client-id: ${ORDERS_CLIENT_ID}
            client-secret: ${ORDERS_CLIENT_SECRET}
            authorization-grant-type: client_credentials
            scope: orders:write
        provider:
          corp-idp:
            token-uri: https://idp.example.com/oauth2/token
contract-first:
  clients:
    orders:
      auth:
        mode: provider
        header-name: Authorization
        scheme: Bearer
```

```java
@Configuration
class OrdersAuthConfig {

    @Bean
    OrdersTokenProvider ordersTokenProvider(OAuth2AuthorizedClientManager clients) {
        return () -> {
            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                    .withClientRegistrationId("orders")
                    .principal("orders-client")   // any stable name for a machine identity
                    .build();
            OAuth2AuthorizedClient client = clients.authorize(request);
            return Optional.ofNullable(client).map(c -> c.getAccessToken().getTokenValue());
        };
    }
}
```

### 5b. Relay the current user's JWT (resource server to resource server)

When your service is itself protected by Spring Security with JWTs and Orders accepts the same tokens:

```java
@Bean
OrdersTokenProvider ordersTokenProvider() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(JwtAuthenticationToken.class::isInstance)
            .map(JwtAuthenticationToken.class::cast)
            .map(auth -> auth.getToken().getTokenValue());
}
```

With `header-name: Authorization` and `scheme: Bearer`. On a servlet thread this is equivalent to propagate mode,
but it also works where the security context is propagated and the request is not, for example in `@Async` methods
with `DelegatingSecurityContextAsyncTaskExecutor`.

### 5c. Secret manager with caching

Fetching from Vault, AWS Secrets Manager or similar on every call would be slow and rate-limited. Wrap the fetching
provider in `CachingTokenProvider`:

```java
@Bean
OrdersTokenProvider ordersTokenProvider(SecretsManagerClient secrets) {
    OrdersTokenProvider fetching = () -> Optional.of(
            secrets.getSecretValue(r -> r.secretId("prod/orders/api-key")).secretString());
    return new CachingTokenProvider(fetching, Duration.ofMinutes(10));
}
```

### 5d. Invalidate the cache on 401 and retry once

Keys get rotated. Catch the typed exception, drop the cached token, retry.

```java
@Service
class Checkout {
    private final OrdersApi orders;
    private final CachingTokenProvider tokens;   // expose the same instance as the bean

    Order place(CreateOrderRequest request) {
        String key = "idem-" + UUID.randomUUID();     // same key on retry: the server replays, never duplicates
        try {
            return orders.createOrder(key, request, null).getBody();
        } catch (OrdersApiException e) {
            if (e.status() != HttpStatus.UNAUTHORIZED) throw e;
            tokens.invalidate();
            return orders.createOrder(key, request, null).getBody();
        }
    }
}
```

```java
@Bean
CachingTokenProvider ordersTokenProvider(SecretsManagerClient secrets) {   // bean type is the decorator
    return new CachingTokenProvider(() -> Optional.of(fetch(secrets)), Duration.ofMinutes(10));
}
```

The retry is safe because the contract makes creation idempotent: the same `Idempotency-Key` with the same payload
returns the original `201`.

## 6. Override the built-in provider in any mode

`@ConditionalOnMissingBean` means your bean replaces the default even in `static` or `propagate` mode. Useful for a
key that rotates at runtime without a restart:

```java
@Bean
OrdersTokenProvider ordersTokenProvider(RotatingKeyStore store) {
    return () -> Optional.of(store.currentKey());   // mode stays 'static' in configuration
}
```

## 7. Explicit token first, configured key as fallback

Mostly machine identity, occasionally on behalf of a caller:

```java
@Bean
OrdersTokenProvider ordersTokenProvider(@Value("${contract-first.clients.orders.auth.token}") String serviceKey) {
    return () -> TokenContext.current().or(() -> Optional.of(serviceKey));
}
```

Wrap a specific call with `TokenContext.with(callerToken, ...)` to switch identity for that call only.

## 8. Two identities against the same API

`@EnableContractClient` owns one group, `orders`. For a second identity register a second group yourself and give it
its own header:

```java
@Configuration
@ImportHttpServices(group = "orders-admin", types = OrdersApi.class)
class OrdersAdminClientConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer ordersAdminAuth(@Value("${orders.admin.api-key}") String adminKey) {
        return groups -> groups.filterByName("orders-admin")
                .forEachClient((group, builder) -> builder.defaultHeader("X-API-Key", adminKey));
    }

    @Bean
    OrdersApi adminOrders(HttpServiceProxyRegistry registry) {
        return registry.getClient("orders-admin", OrdersApi.class);
    }
}
```

```yaml
spring:
  http:
    serviceclient:
      orders-admin:
        base-url: https://orders.example.com/api/v1
```

Inject by qualifier: `@Qualifier("adminOrders") OrdersApi admin`. The default group keeps the library's token
interceptor and error handling; the second group gets only what you add, so copy `ProblemResponseErrorHandler`
behaviour if you want typed exceptions there too.

## 9. Different inbound and outbound header names

Propagate mode reads the configured header name. If callers send `Authorization: Bearer <jwt>` but Orders wants the
raw value in `X-API-Key`:

```java
@Bean
OrdersTokenProvider ordersTokenProvider() {
    return () -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
            .filter(ServletRequestAttributes.class::isInstance)
            .map(a -> ((ServletRequestAttributes) a).getRequest().getHeader("Authorization"))
            .map(v -> v.replaceFirst("(?i)^Bearer ", ""));
}
```

With `header-name: X-API-Key` and no scheme.

## 10. Mutual TLS in addition to the token

Transport authentication is Boot's `ssl.bundle` on the group; it combines with any token mode:

```yaml
spring:
  ssl:
    bundle:
      pem:
        orders:
          keystore:
            certificate: file:/etc/orders/client.crt
            private-key: file:/etc/orders/client.key
          truststore:
            certificate: file:/etc/orders/ca.crt
  http:
    serviceclient:
      orders:
        base-url: https://orders.example.com/api/v1
        ssl:
          bundle: orders
```

## 11. Handling authentication failures

A rejected token comes back as a spec-conformant problem and surfaces as `OrdersApiException`:

```java
try {
    orders.listOrders(null, null, null, null, null, 0, 20);
} catch (OrdersApiException e) {
    if (e.status() == HttpStatus.UNAUTHORIZED) {
        ProblemDetail problem = e.problem().orElseThrow();   // title "Unauthorized", detail "Unrecognised API key"
        ...
    }
}
```

A token that could not be resolved at all never reaches the network:

```java
try {
    orders.getOrder(id, null);
} catch (ClientAuthenticationException e) {
    // "No token available for GET http://... (mode PROPAGATE): wrap the call in TokenContext.with(token, ...) ..."
}
```

## 12. Tests

In a consumer's tests use static mode with a dummy value, or a `@TestConfiguration` provider:

```java
@SpringBootTest(properties = {
        "spring.http.serviceclient.orders.base-url=http://orders.test/api/v1",
        "contract-first.clients.orders.auth.token=test-key"})
class CheckoutTest { ... }
```

```java
@TestConfiguration
class FixedTokenConfig {
    @Bean OrdersTokenProvider ordersTokenProvider() { return () -> Optional.of("test-key"); }
}
```

To assert what was sent, bind a `MockRestServiceServer` to the group's builder with a low-priority
`RestClientHttpServiceGroupConfigurer`, exactly as `MockedConsumerApp` in this repository's client tests does.

## Which mode when

| Situation | Mode |
|-----------|------|
| Service with its own fixed credential | `static` |
| Gateway/BFF forwarding the caller's credential | `propagate` |
| Non-web code acting for a specific caller | `propagate` + `TokenContext` |
| Credential obtained from an IdP or secret store | `provider` (+ `CachingTokenProvider`) |
| Relay the current user's JWT | `provider` reading `SecurityContextHolder` |
| Credential rotates at runtime | any mode, custom `OrdersTokenProvider` |
| Several identities | extra HTTP service group |
