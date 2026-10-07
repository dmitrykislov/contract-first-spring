package io.github.dmitrykislov.contractfirst.client.autoconfigure;

import io.github.dmitrykislov.contractfirst.client.ContractClientRegistration;
import io.github.dmitrykislov.contractfirst.client.EnableContractClient;
import io.github.dmitrykislov.contractfirst.client.config.ContractClientsPropertyBinder;
import io.github.dmitrykislov.contractfirst.client.errors.ApiExceptionFactory;
import io.github.dmitrykislov.contractfirst.client.errors.ApiException;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;
import java.util.Map;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/**
 * Registers, per {@link EnableContractClient}, the group's configurer (token, retry, request id, JSON,
 * errors) and a {@link ContractClientRegistration} marker used for startup validation.
 */
public final class ContractClientBeanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

    private Environment environment;
    private BeanFactory beanFactory;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableContractClient.class.getName(), true);
        if (attrs == null) {
            return;
        }
        String group = (String) attrs.get("group");
        if (!enabled(environment, group)) {
            return;
        }
        Class<? extends ApiException> exception = (Class<? extends ApiException>) load((String) attrs.get("exception"));
        Class<? extends TokenProvider> tokenProvider = (Class<? extends TokenProvider>) load((String) attrs.get("tokenProvider"));
        ApiExceptionFactory.forType(exception); // fail at startup, not on the first error response

        ContractClientRegistration registration = new ContractClientRegistration(group, exception, tokenProvider);
        registry.registerBeanDefinition(group + "ContractClientRegistration",
                BeanDefinitionBuilder.genericBeanDefinition(ContractClientRegistration.class, () -> registration).getBeanDefinition());
        BeanFactory factory = this.beanFactory;
        registry.registerBeanDefinition(group + "ContractClientGroupConfigurer",
                BeanDefinitionBuilder.genericBeanDefinition(ContractClientGroupConfigurer.class,
                        () -> new ContractClientGroupConfigurer(registration, factory)).getBeanDefinition());
    }

    static boolean enabled(Environment environment, String group) {
        return environment.getProperty(ContractClientsPropertyBinder.groupPrefix(group) + ".enabled", Boolean.class, true);
    }

    private static Class<?> load(String name) {
        return ClassUtils.resolveClassName(name, ContractClientBeanRegistrar.class.getClassLoader());
    }
}
