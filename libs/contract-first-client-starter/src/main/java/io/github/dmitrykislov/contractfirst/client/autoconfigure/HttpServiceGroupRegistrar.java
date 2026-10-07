package io.github.dmitrykislov.contractfirst.client.autoconfigure;

import io.github.dmitrykislov.contractfirst.client.EnableContractClient;
import java.util.Map;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.service.registry.AbstractHttpServiceRegistrar;

/** Registers the generated interfaces named by {@link EnableContractClient} as an HTTP service group. */
public final class HttpServiceGroupRegistrar extends AbstractHttpServiceRegistrar implements EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        super.setEnvironment(environment);
        this.environment = environment;
    }

    @Override
    protected void registerHttpServices(GroupRegistry registry, AnnotationMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableContractClient.class.getName(), true);
        if (attrs == null) {
            return;
        }
        String group = (String) attrs.get("group");
        if (!ContractClientBeanRegistrar.enabled(environment, group)) {
            return;
        }
        GroupRegistry.GroupSpec spec = registry.forGroup(group);
        String[] basePackageClasses = (String[]) attrs.get("basePackageClasses");
        String[] basePackages = (String[]) attrs.get("basePackages");
        String[] types = (String[]) attrs.get("types");
        if (basePackageClasses.length > 0) {
            spec.detectInBasePackages(packagesOf(basePackageClasses));
        }
        if (basePackages.length > 0) {
            spec.detectInBasePackages(basePackages);
        }
        if (types.length > 0) {
            spec.registerTypeNames(types);
        }
        if (basePackageClasses.length + basePackages.length + types.length == 0) {
            throw new IllegalStateException("@EnableContractClient(group=\"" + group
                    + "\") must name basePackageClasses, basePackages or types");
        }
    }

    private static String[] packagesOf(String[] classNames) {
        String[] packages = new String[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            int dot = classNames[i].lastIndexOf('.');
            packages[i] = dot > 0 ? classNames[i].substring(0, dot) : "";
        }
        return packages;
    }
}
