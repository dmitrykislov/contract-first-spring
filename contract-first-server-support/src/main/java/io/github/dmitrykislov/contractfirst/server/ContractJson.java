package io.github.dmitrykislov.contractfirst.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.openapitools.jackson.nullable.JsonNullableJackson3Module;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON policy a contract needs at the HTTP boundary, as a mapper derived from the application's
 * own: unset optional properties are omitted (they are not nullable in the schema) and
 * {@code nullable: true} properties are {@code JsonNullable}. Deliberately <em>not</em> the global
 * mapper, so messaging, caching or logging elsewhere in the service keep their own settings.
 */
public final class ContractJson {

    private final JsonMapper mapper;

    public ContractJson(JsonMapper applicationMapper) {
        this.mapper = applicationMapper.rebuild()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(new JsonNullableJackson3Module())
                .build();
    }

    public JsonMapper mapper() {
        return mapper;
    }
}
