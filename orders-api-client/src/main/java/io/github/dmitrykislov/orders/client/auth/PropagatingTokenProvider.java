package io.github.dmitrykislov.orders.client.auth;

import io.github.dmitrykislov.orders.client.OrdersClientProperties;
import java.util.Optional;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Passes on the current caller's token: first an explicit {@link TokenContext}, then the same header
 * on the servlet request this thread is handling (server-to-server calls on behalf of the caller).
 * A scheme on the incoming header ({@code Bearer abc}) is stripped so the outgoing header is not
 * prefixed twice. The servlet lookup is skipped entirely when the Servlet API is not on the classpath.
 */
public final class PropagatingTokenProvider implements OrdersTokenProvider {

    private static final boolean SERVLET_PRESENT = ClassUtils.isPresent(
            "jakarta.servlet.http.HttpServletRequest", PropagatingTokenProvider.class.getClassLoader());

    private final OrdersClientProperties.Auth auth;

    public PropagatingTokenProvider(OrdersClientProperties.Auth auth) {
        this.auth = auth;
    }

    @Override
    public Optional<String> token() {
        return TokenContext.current().or(this::fromCurrentServletRequest);
    }

    private Optional<String> fromCurrentServletRequest() {
        if (!SERVLET_PRESENT) {
            return Optional.empty();
        }
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(attributes -> attributes.getRequest().getHeader(auth.headerName()))
                .map(auth::rawToken)
                .filter(value -> !value.isBlank());
    }
}
