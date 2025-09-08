package ai.timefold.wasm.service.classgen;

import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

import com.dylibso.chicory.runtime.Instance;

public class Allocator {
    private final IntUnaryOperator alloc;
    private final IntConsumer dealloc;

    public Allocator(Instance instance, String allocFunctionName, String deallocFunctionName) {
        var allocFunction = instance.export(allocFunctionName);
        var deallocFunction = instance.export(deallocFunctionName);

        alloc = memorySize -> (int) allocFunction.apply(memorySize)[0];
        dealloc = deallocFunction::apply;
    }

    public int allocate(int memorySize) {
        return alloc.applyAsInt(memorySize);
    }

    public void free(int pointer) {
        dealloc.accept(pointer);
    }
}
