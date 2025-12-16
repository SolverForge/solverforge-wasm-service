package org.solverforge.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;

import org.solverforge.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public record MapComponent(@JsonProperty("mapper") List<WasmFunction> mappers) implements StreamComponent {
    @JsonCreator
    public MapComponent {
    }

    public MapComponent() {
        this(Collections.emptyList());
    }

    @Override
    public String kind() {
        return "map";
    }

    @Override
    public void applyToDataStream(DataStream dataStream) {
        dataStream.setSize(mappers.size());
    }
}
