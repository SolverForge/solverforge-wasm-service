package org.solverforge.wasm.service.dto.constraint;

import org.solverforge.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record FilterComponent(@JsonProperty("predicate") WasmFunction filter) implements StreamComponent {
    @JsonCreator
    public FilterComponent {
    }

    public FilterComponent() {
        this(null);
    }

    @Override
    public String kind() {
        return "filter";
    }
}
