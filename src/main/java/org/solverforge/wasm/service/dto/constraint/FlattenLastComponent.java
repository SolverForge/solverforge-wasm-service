package org.solverforge.wasm.service.dto.constraint;

import org.solverforge.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record FlattenLastComponent(
        @Nullable @JsonProperty("map") WasmFunction map,
        @Nullable @JsonProperty("elementType") String elementType) implements StreamComponent {
    @JsonCreator
    public FlattenLastComponent {
    }

    public FlattenLastComponent() {
        this(null, null);
    }

    @Override
    public String kind() {
        return "flattenLast";
    }
}
