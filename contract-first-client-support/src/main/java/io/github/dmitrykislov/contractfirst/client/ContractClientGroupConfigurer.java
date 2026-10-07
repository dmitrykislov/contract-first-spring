package io.github.dmitrykislov.contractfirst.client;

import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies {@link ContractClientSupport} to one group when Spring Boot builds the group's
 * {@code RestClient}. Everything is resolved lazily at that point (singleton instantiation), so the
 * order in which configuration classes were processed does not matter and a missing or misconfigured
 * token provider fails startup with a message naming the group.
 */
final class ContractClientGroupConfigurer implements RestClientHttpServiceGroupConfigurer, Ordered {

    /** After Boot's property-driven configurer and after RestClientCustomizers, before application overrides. */
    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    private final ContractClientRegistration registration;
    private final BeanFactory beanFactory;

    ContractClientGroupConfigurer(ContractClientRegistration registration, BeanFactory beanFactory) {
        this.registration = registration;
        this.beanFactory = beanFactory;
    }

    @Override
    public void configureGroups(Groups<org.springframework.web.client.RestClient.Builder> groups) {
        String group = registration.group();
        ClientSettings settings = beanFactory.getBean(ContractClientsProperties.class).settingsFor(group);
        TokenProvider tokenProvider = resolveTokenProvider(settings);
        JsonMapper jsonMapper = beanFactory.getBeanProvider(JsonMapper.class).getIfAvailable(() -> JsonMapper.builder().build());

        ContractClientSupport support = ContractClientSupport.forGroup(group)
                .auth(settings.auth(), tokenProvider)
                .retry(settings.retry())
                .requestId(settings.requestId())
                .jsonMapper(jsonMapper)
                .exceptions(ApiExceptionFactory.forType(registration.exceptionType()));
        groups.filterByName(group).forEachClient((g, builder) -> support.customize(builder));
    }

    private TokenProvider resolveTokenProvider(ClientSettings settings) {
        Class<? extends TokenProvider> type = registration.tokenProviderType();
        TokenProvider configured;
        String description;
        if (type != TokenProvider.class) {
            ObjectProvider<? extends TokenProvider> provider = beanFactory.getBeanProvider(type);
            configured = provider.getIfUnique();
            description = "a unique bean of type " + type.getName();
        } else {
            String name = registration.tokenProviderBeanName();
            configured = beanFactory.containsBean(name) ? beanFactory.getBean(name, TokenProvider.class) : null;
            description = "a TokenProvider bean named '" + name + "'";
        }
        if (configured != null) {
            return configured;
        }
        try {
            return ContractClientSupport.tokenProvider(settings.auth(), description);
        } catch (IllegalStateException e) {
            // No cause on purpose: the group-qualified message must be the root cause a failure report shows.
            throw new IllegalStateException(ContractClientsProperties.PREFIX + "." + registration.group() + ": " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
