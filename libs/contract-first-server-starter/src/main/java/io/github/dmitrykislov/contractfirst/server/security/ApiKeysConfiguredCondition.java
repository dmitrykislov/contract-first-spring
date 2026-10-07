package io.github.dmitrykislov.contractfirst.server.security;

import io.github.dmitrykislov.contractfirst.server.ContractServerProperties;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when at least one API key is configured, whether as a comma-separated value
 * ({@code keys=a,b}) or as a YAML/properties list ({@code keys[0]=a}). A plain
 * {@code @ConditionalOnProperty} would miss the list form, because no property named exactly
 * {@code ...keys} exists then.
 */
final class ApiKeysConfiguredCondition extends SpringBootCondition {

    static final String PROPERTY = ContractServerProperties.PREFIX + ".api-key.keys";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Set<String> keys = Binder.get(context.getEnvironment())
                .bind(PROPERTY, Bindable.setOf(String.class))
                .orElse(Set.of());
        ConditionMessage.Builder message = ConditionMessage.forCondition("API keys configured");
        return keys.isEmpty()
                ? ConditionOutcome.noMatch(message.didNotFind("any value for " + PROPERTY).atAll())
                : ConditionOutcome.match(message.found("key(s) under " + PROPERTY).items(keys.size()));
    }
}
