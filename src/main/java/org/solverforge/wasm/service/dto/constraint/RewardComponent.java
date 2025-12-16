package org.solverforge.wasm.service.dto.constraint;

import org.solverforge.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public record RewardComponent(@JsonProperty("weight") String weight, @Nullable @JsonProperty("scaleBy") WasmFunction scaleBy) implements StreamComponent {
    @JsonCreator
    public RewardComponent {
    }

    public RewardComponent() {
        this("", null);
    }

    @Override
    public String kind() {
        return "reward";
    }
}
