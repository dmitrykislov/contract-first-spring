package io.github.dmitrykislov.contractfirst.client;

import io.github.dmitrykislov.contractfirst.client.errors.ApiException;
import io.github.dmitrykislov.contractfirst.client.auth.TokenProvider;

/**
 * What one {@link EnableContractClient} registered. Exposed as a bean so startup validation can compare
 * configured groups with registered ones, and so applications can list the contract clients they carry.
 */
public record ContractClientRegistration(
        String group,
        Class<? extends ApiException> exceptionType,
        Class<? extends TokenProvider> tokenProviderType) {

    /** Bean name consulted when {@link #tokenProviderType()} is the plain {@link TokenProvider}. */
    public String tokenProviderBeanName() {
        return group + "TokenProvider";
    }
}
