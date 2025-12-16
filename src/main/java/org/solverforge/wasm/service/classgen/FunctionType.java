package org.solverforge.wasm.service.classgen;

import static org.solverforge.wasm.service.classgen.DomainObjectClassGenerator.getDescriptor;

import java.lang.constant.ClassDesc;

import ai.timefold.solver.core.api.function.TriFunction;
import org.solverforge.wasm.service.SolverResource;
import org.solverforge.wasm.service.dto.WasmFunction;
import org.solverforge.wasm.service.dto.constraint.DataStream;

import com.dylibso.chicory.runtime.Instance;

public enum FunctionType {
    PREDICATE(WasmFunction::asPredicate),
    MAPPER(WasmFunction::asFunction),
    LIST_MAPPER(WasmFunction::asToListFunction),
    INT_LIST_MAPPER(WasmFunction::asToIntListFunction),
    TO_INT(WasmFunction::asToIntFunction),
    TO_LONG(WasmFunction::asToLongFunction);

    private final TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor;

    FunctionType(TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor) {
        this.functionConvertor = functionConvertor;
    }

    public ClassDesc getClassDescriptor(DataStream dataStream, int argCount) {
        return getDescriptor(switch (this) {
            case PREDICATE -> dataStream.getPredicateClassOfSize(argCount);
            case MAPPER, LIST_MAPPER, INT_LIST_MAPPER -> dataStream.getFunctionClassOfSize(argCount);
            case TO_INT -> dataStream.getToIntFunctionClassOfSize(argCount);
            case TO_LONG -> dataStream.getToLongFunctionClassOfSize(argCount);
        });
    }

    public Object getFunction(int size, WasmFunction wasmFunction) {
        return functionConvertor.apply(wasmFunction, size, SolverResource.INSTANCE.get());
    }
}
