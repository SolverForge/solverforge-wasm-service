package ai.timefold.wasm.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DomainAccessor(@JsonProperty("getter") String getterFunctionName,
                             @JsonProperty("setter") String setterFunctionName) {
}
