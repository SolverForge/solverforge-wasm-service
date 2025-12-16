package org.solverforge.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;

import org.solverforge.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record ExpandComponent(@JsonProperty("mapper") List<WasmFunction> mappers) implements StreamComponent {
    @JsonCreator
    public ExpandComponent {
    }

    public ExpandComponent() {
        this(Collections.emptyList());
    }

    @Override
    public String kind() {
        return "expand";
    }

    @Override
    public void applyToDataStream(DataStream dataStream) {
        dataStream.setSize(dataStream.getTupleSize() + mappers.size());
    }
}
