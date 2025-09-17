package ai.timefold.wasm.service.classgen;

import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

import com.dylibso.chicory.runtime.Instance;

public class Allocator {
    private final IntUnaryOperator alloc;
    private final IntConsumer dealloc;
    private final IntConsumer solutionDealloc;

    public Allocator(Instance instance, String allocFunctionName, String deallocFunctionName,
            String solutionDeallocFunctionName) {
        var allocFunction = instance.export(allocFunctionName);
        var deallocFunction = instance.export(deallocFunctionName);
        var solutionDeallocFunction = instance.export(solutionDeallocFunctionName);

        alloc = memorySize -> (int) allocFunction.apply(memorySize)[0];
        dealloc = deallocFunction::apply;
        solutionDealloc = solutionDeallocFunction::apply;
    }

    public int allocate(int memorySize) {
        return alloc.applyAsInt(memorySize);
    }

    public void free(int pointer) {
        dealloc.accept(pointer);
    }

    public void freeSolution(int pointer) {
        solutionDealloc.accept(pointer);
    }
}
