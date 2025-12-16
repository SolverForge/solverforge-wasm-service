package org.solverforge.wasm.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DomainObjectMapper(@JsonProperty("fromString") String stringToInstanceFunction,
                                 @JsonProperty("toString") String instanceToStringFunction) {
}
