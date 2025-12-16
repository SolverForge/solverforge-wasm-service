package org.solverforge.wasm.service.dto.constraint;

import static org.solverforge.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.solverforge.wasm.service.classgen.DataStreamInfo;
import org.solverforge.wasm.service.classgen.FunctionType;
import org.solverforge.wasm.service.classgen.WasmObject;
import org.solverforge.wasm.service.dto.WasmFunction;
import org.solverforge.wasm.service.dto.constraint.joiner.DataJoiner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record ComplementComponent(@JsonProperty("className") String className, @Nullable @JsonProperty("padding") List<WasmFunction> padding) implements StreamComponent {
    @JsonCreator
    public ComplementComponent {
    }

    public ComplementComponent() {
        this(null, null);
    }

    public ComplementComponent(String className) {
        this(className, Collections.emptyList());
    }

    @Override
    public String kind() {
        return "complement";
    }

    public void loadPadding(DataStreamInfo dataStreamInfo) {
        if (padding != null && !padding.isEmpty()) {
            for (var padder : padding) {
                dataStreamInfo.loadFunctionOfSize(FunctionType.MAPPER, padder, 1);
            }
        } else {
            for (var i = 0; i < dataStreamInfo.dataStream().getTupleSize() - 1; i++) {
                dataStreamInfo.codeBuilder().getstatic(getDescriptor(WasmObject.class), "CONSTANT_NULL",
                        getDescriptor(Function.class));
            }
        }
    }
}
