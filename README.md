# contract-first-spring

[![CI](https://github.com/dmitrykislov/contract-first-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/dmitrykislov/contract-first-spring/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-007396) ![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)

**Contract-first REST with Spring Boot 4: one OpenAPI document becomes Java bindings, auto-configured declarative
clients, server interfaces, and tests that prove both sides honour the contract.**

One OpenAPI document is the source of truth. From it this build produces, every time it runs:

| You get | How | Module |
|---------|-----|--------|
| **Java bindings** (request/response models) | OpenAPI Generator 7.26, Spring generator, Jackson 3 | generated into `orders-api-client` and `orders-api-server` |
| **Declarative HTTP clients** (`@HttpExchange` interfaces) auto-configured as a Spring Boot 4 HTTP service group with base URL, timeouts, TLS and pluggable authentication | generator library `spring-http-interface` + a small auto-configuration | `orders-api-client` |
| **Server API interfaces** that controllers must implement; a missing operation is a compile error | generator library `spring-boot`, `interfaceOnly` | `orders-api-server` |
| **Proof the server implements the contract** | MockMvc tests whose every exchange is validated against the spec, plus a route-coverage test | `orders-api-server` |
| **Proof client and server agree over real HTTP** | the server on a random port, driven through the generated client, every exchange validated | `orders-api-e2e` |

```bash
git clone https://github.com/dmitrykislov/contract-first-spring.git
cd contract-first-spring
mvn verify        # generate → compile → 82 unit/integration tests → 8 end-to-end scenarios
```

Then run the sample server and call it:

```bash
java -jar orders-api-server/target/orders-api-server-0.1.0-SNAPSHOT-exec.jar
curl -s -H 'X-API-Key: dev-api-key-1' localhost:8080/api/v1/catalog/products/WIDGET-BLUE-L
curl -s localhost:8080/api/v1/orders            # 401 as application/problem+json
```

## The problem this solves

Teams that publish a REST API usually hand-write three things that must agree: the documentation, the server, and
one client per consumer. They drift. The server adds a field the spec never mentions; a client sends `null` where
the schema says the property is optional but not nullable; an error path returns HTML where the contract promised
`application/problem+json`. Nothing fails until a consumer breaks in production.

Contract-first turns the spec into code and the agreement into tests:

* the **models and interfaces are generated**, so client and server cannot disagree about shapes or routes;
* the **server implements interfaces**, so the compiler reports a missing or mistyped operation;
* **every test exchange is validated against the spec** by an independent validator, so a wrong status code, header,
  content type or body shape is a red test, on both the server side (MockMvc) and the client side (real HTTP).

While building this repository the validator caught six real defects in code that otherwise "worked": undeclared
`400` responses, a relative `instance` URI where the schema wanted an absolute one, `null`s leaking for optional
properties, a test property file silently overriding production JSON settings, and a client formatting
`date-time` query parameters in a way the server happened to tolerate. See "What the conformance tests caught".

## Layout

```
contract-first-spring/
├── orders-api-spec/          the contract (src/main/resources/openapi/orders-api.yaml) + lint tests
├── orders-api-test-support/  shared test library: spec validator for MockMvc and RestClient, operation listing
├── orders-api-client/        generated models + @HttpExchange clients, auto-configuration, token strategies
├── orders-api-server/        generated API interfaces + models, controllers, domain, conformance tests
└── orders-api-e2e/           Failsafe integration tests: real server ⇄ generated client, contract-validated
```

The sample contract is a small **Orders API** chosen to exercise what real APIs need: every verb, path and query
parameters (including arrays and `date-time`), request headers (`Idempotency-Key`, `If-Match`, `X-Request-Id`,
`Accept-Language`), an API-key security scheme, JSON bodies, `201 Created` with `Location`, `ETag`, `204`, JSON
Merge Patch with a nullable property, and RFC 9457 problem responses for 400/401/404/409/412/422.

## Using the client from your Spring Boot 4 application

```xml
<dependency>
  <groupId>io.github.dmitrykislov</groupId>
  <artifactId>orders-api-client</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
spring:
  http:
    serviceclient:
      orders:                          # the HTTP service group registered by the auto-configuration
        base-url: https://orders.example.com/api/v1
        connect-timeout: 2s
        read-timeout: 5s
        redirects: dont-follow         # optional; also: default-header, ssl.bundle, apiversion
orders:
  client:
    enabled: true                      # default
    auth:
      mode: static                     # static | propagate | provider
      token: ${ORDERS_API_KEY}         # static mode
      header-name: X-API-Key           # default: the contract's ApiKeyAuth header
      scheme:                          # optional, e.g. Bearer → "Authorization: Bearer <token>"
```

```java
@Service
class Checkout {
    private final OrdersApi orders;                      // generated interface, injected as a bean

    Order place(CreateOrderRequest request) {
        try {
            return orders.createOrder("idem-" + UUID.randomUUID(), request, null).getBody();
        } catch (OrdersApiException e) {                 // any 4xx/5xx, with Spring's ProblemDetail
            e.problem().ifPresent(p -> log.warn("{} {}", p.getType(), p.getDetail()));
            e.fieldErrors().forEach(f -> log.warn("  {}: {}", f.getField(), f.getMessage()));
            throw e;
        }
    }
}
```

Connection settings are Spring Boot's own `spring.http.serviceclient.<group>.*` properties; the client adds nothing
to them. What it adds:

* **Authentication modes** (`orders.client.auth.mode`), resolved per request:

  | Mode | Token source | Typical use |
  |------|--------------|-------------|
  | `static` (default) | `orders.client.auth.token` | one service identity per deployment |
  | `propagate` | the caller's token: `TokenContext.with(token, () -> …)` first, otherwise the same header of the servlet request being handled (scheme stripped, never prefixed twice) | gateways, services acting on behalf of the caller |
  | `provider` | your `OrdersTokenProvider` bean, asked on every call | tokens fetched from an identity provider or secret manager (cache in the bean) |

  Any mode can be overridden by defining an `OrdersTokenProvider` bean. If no token can be resolved the call fails
  before anything is sent, with an `OrdersClientAuthenticationException` naming the mode and the remedy.
  `TokenContext` is built on Java 25 `ScopedValue`, so it propagates into virtual threads and structured
  concurrency without `ThreadLocal` leaks.

* **Contract-safe JSON**: unset optional fields are omitted (never `null`), and `nullable: true` properties such as
  `OrderPatch.notes` are `JsonNullable`, so `new OrderPatch()` keeps notes, `.notes(null)` clears them and
  `.notes("x")` sets them. This holds regardless of the consumer's global Jackson settings.

* **Typed errors**: 4xx/5xx become `OrdersApiException` carrying Spring's `ProblemDetail` and, for validation
  failures, the contract's `FieldError` list. Non-problem bodies (proxy HTML) are kept only as a short excerpt.

## How each piece works

### Code generation (root `pom.xml`, `pluginManagement`)

Shared options: `useSpringBoot4`, `useJackson3`, `useTags` (one interface per tag), `generateBuilders`,
`openApiNullable` (nullable properties → `JsonNullable`), `containerDefaultToNull` (absent arrays bind to `null`, so a
PATCH can tell "not sent" from "cleared"), no Swagger annotations.

* **Client**: `library=spring-http-interface`, bean validation off (constraints are inert on a proxy; the server
  validates). Output: `io.github.dmitrykislov.orders.client.api.*`, `.model.*`.
* **Server**: `library=spring-boot`, `interfaceOnly`, `skipDefaultInterface` (no default methods, so every operation
  must be implemented), `requestMappingMode=api_interface`, Spring 7 built-in method validation. The base path from
  `servers[0].url` becomes `@RequestMapping("${openapi.orders.base-path:/api/v1}")`; the server sets that property
  once in `application.yaml` and the API-key filter reads the same value.

Client and server generate their own model packages on purpose: they are separate deployables, and the e2e module
has both on one classpath.

### Client module

`OrdersClientAutoConfiguration` registers the generated interfaces in HTTP service group `orders` with
`@ImportHttpServices`. It is ordered **before** Boot's `HttpServiceClientAutoConfiguration`, which is conditional on
the registry bean that `@ImportHttpServices` contributes (an easy thing to get wrong). A
`RestClientHttpServiceGroupConfigurer`, ordered after Boot's property-driven one, adds the token interceptor, the
contract-safe `JsonMapper` and the problem-aware status handler.

Tests bind a `MockRestServiceServer` to the group's `RestClient.Builder` and check: property binding, URL building for
every parameter kind (path, repeated query arrays, RFC 3339 `date-time`, headers), request bodies including the
absent/null/value distinction, response decoding, error mapping, the `enabled` switch, and all three authentication
modes. `ClientContractCoverageTest` scans the generated package and asserts that every operation in the YAML has
exactly one `@HttpExchange` method named after its operationId, so a changed generator option fails here.

### Server module

Controllers implement the generated interfaces and only translate to a small domain (`OrderService`, `OrderDraft`,
sealed `OrdersDomainException`, `Change<T>` for merge-patch semantics). `ApiExceptionHandler` extends
`ResponseEntityExceptionHandler`: framework exceptions keep Spring's status mapping, every response is decorated with
the API's `type` namespace and an absolute `instance`, validation failures add the contract's `errors` extension, and
domain failures are mapped with an exhaustive pattern-matching `switch`. Everything is a `ProblemDetail`, including
the 401 written by `ApiKeyAuthenticationFilter`.

Domain rules the sample implements: idempotent creation (same key + same payload replays the original `201`,
different payload is `409`), single-currency orders (`422`), optimistic locking with `If-Match`/`ETag` (`412`, malformed
header is `400`), state transitions (`409`), recorded cancellation reason, and `Accept-Language` lookup per RFC 4647.

Tests: `MockMvcTester` scenarios for every operation and error path, each asserting behaviour **and** calling
`MockMvcContract.assertExchangeConforms` (request and response) or `assertResponseConforms` (deliberately invalid
requests). `ContractCoverageTest` diffs the YAML's operations against Spring MVC's actual handler mappings.
`OrdersServerPropertiesTest` proves the server refuses to start without API keys.

### Test-support module

`OrdersContract` loads the YAML from the classpath and builds the validator; `MockMvcContract` adapts MockMvc
results; `ContractValidatingInterceptor` validates real `RestClient` exchanges (decoding query values first, which the
library's own interceptor does not); `ContractOperation` lists the contract's operations for coverage tests.

### End-to-end module

`OrdersEndToEndIT` (Failsafe, `mvn verify`) boots `OrdersServerApplication` on a random port, then boots a separate
consumer context configured only by properties, exactly like a deployed client. Scenarios: the full lifecycle, every
business error as a typed exception, wrong API key, server-side validation through a non-validating consumer,
a client bug caught before the request leaves, catalog lookups, and the `propagate` and `provider` token modes.

## Which libraries, and why

| Need | Choice | Notes |
|------|--------|-------|
| Generate from OpenAPI | **OpenAPI Generator 7.26.0**, `spring` generator | Native `useSpringBoot4`/`useJackson3`; `spring-http-interface` library for `@HttpExchange` clients; `interfaceOnly` for servers. |
| Declarative client runtime | **Spring Framework 7 HTTP service clients** + **Spring Boot 4.1.1** `@ImportHttpServices` groups | Boot binds base URL, timeouts, redirects, SSL, default headers and API versioning per group with no code. Spring Cloud OpenFeign is in maintenance mode and unnecessary. |
| Validate exchanges against the spec | **Atlassian `openapi-request-validator` 3.0.0** (renamed from `swagger-request-validator`), core module | Framework-agnostic; used for MockMvc assertions and the e2e interceptor. Its `spring-web-client` module validates percent-encoded query values and rejects valid `date-time` parameters, hence the 60-line interceptor in test-support. |
| Nullable properties | **`jackson-databind-nullable` 0.2.12** | Ships `JsonNullableJackson3Module`; registered on both sides. |
| Spec parsing and lint | **swagger-parser 2.1.48** | |

Considered and not used: Spring Cloud Contract (its own DSL, not OpenAPI), springdoc-openapi (code-first, the
opposite direction, though 3.1.x works with Boot 4 if you also want to publish `/v3/api-docs`), hand-written client
wrappers (duplicate the contract).

## What the conformance tests caught

All fixed; kept here because they are exactly the class of defect this setup exists to find.

* Four operations could return `400` (malformed UUID or SKU pattern) without the spec declaring it; PUT could return
  `422` for an unknown SKU without declaring it.
* `Problem.instance` was a path (`/api/v1/orders`) where the schema requires an absolute `uri`.
* A test `application.yaml` in the e2e module shadowed the server's and switched off `NON_NULL` inclusion, producing
  `"errors": null`. JSON settings now live in code.
* The client sent `"notes": null` for unset optional fields; it now pins its own serialisation policy.
* `OffsetDateTime` query values were checked for RFC 3339 by the client-side validator; the server tolerated the
  looser format, another server might not.

## Adapting to your own contract

1. Replace `orders-api-spec/src/main/resources/openapi/orders-api.yaml`; keep `servers[0].url` ending in the base
   path you want mounted, and update `orders.spec.file` in the root POM if you rename it.
2. Rename packages and the group in the two generator executions and in `OrdersClientAutoConfiguration`.
3. Implement the regenerated server interfaces; the compiler lists what is missing, `ContractCoverageTest` lists
   what is not routed.
4. Keep the three test layers: spec lint, MockMvc conformance, end-to-end through the client.

## Requirements and conventions

Java 25 and Maven 3.9 or later (enforced by the Enforcer plugin). The first build downloads Spring Boot 4.1.1 and
OpenAPI Generator 7.26.0 (~40 MB). Unit and integration tests run under Surefire with `mvn test`; the end-to-end
suite runs under Failsafe with `mvn verify`. The runnable server is
`orders-api-server/target/orders-api-server-<version>-exec.jar`. Generated sources live under `target/` and are
never committed; the YAML is the only artefact to review in a pull request.

The Orders domain, the `example.com` hosts in the spec and the demo API keys in `application.yaml` are placeholders
for the sample; the `io.github.dmitrykislov` coordinates are the only thing specific to this repository.

## License

Apache License 2.0, see [LICENSE](LICENSE).
