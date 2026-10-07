package io.github.dmitrykislov.contractfirst.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Guards the client generator configuration: every operation in the contract must appear as exactly
 * one {@code @HttpExchange} method, named after its operationId, on an interface in the generated API
 * package. A changed {@code useTags}, package or template fails here, not in production.
 *
 * <pre>
 * class ClientContractCoverageTest extends ClientContractCoverageSupport {
 *     protected Contract contract() { return Contract.fromClasspath(OrdersContract.RESOURCE, OrdersContract.BASE_PATH); }
 *     protected String apiPackage() { return OrdersApi.class.getPackageName(); }
 * }
 * </pre>
 */
public abstract class ClientContractCoverageSupport {

    protected abstract Contract contract();

    /** The package the generator wrote the {@code @HttpExchange} interfaces into. */
    protected abstract String apiPackage();

    @Test
    void everyContractOperationHasExactlyOneHttpExchangeMethod() {
        Map<String, String> actual = new TreeMap<>(); // "METHOD /path" -> methodName
        for (Class<?> api : generatedApiInterfaces()) {
            for (Method method : api.getDeclaredMethods()) {
                HttpExchange exchange = AnnotatedElementUtils.findMergedAnnotation(method, HttpExchange.class);
                if (exchange == null) {
                    continue;
                }
                String route = exchange.method() + " " + exchange.value();
                assertThat(actual).as("duplicate route %s", route).doesNotContainKey(route);
                actual.put(route, method.getName());
            }
        }

        Map<String, String> expected = new TreeMap<>();
        contract().operations().forEach(op -> expected.put(op.method() + " " + op.path(), op.operationId()));

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private Set<Class<?>> generatedApiInterfaces() {
        // The default provider only yields concrete classes; the generated APIs are interfaces.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        TypeFilter interfaces = (reader, factory) -> reader.getClassMetadata().isInterface();
        scanner.addIncludeFilter(interfaces);
        Set<Class<?>> result = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
        scanner.findCandidateComponents(apiPackage())
                .forEach(bd -> result.add(ClassUtils.resolveClassName(bd.getBeanClassName(), null)));
        assertThat(result).as("generated API interfaces in %s", apiPackage()).isNotEmpty();
        return result;
    }
}
