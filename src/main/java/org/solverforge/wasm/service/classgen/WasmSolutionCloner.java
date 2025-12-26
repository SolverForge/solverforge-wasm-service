package org.solverforge.wasm.service.classgen;

import java.lang.ref.Cleaner;
import java.lang.reflect.InvocationTargetException;

import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.cloner.SolutionCloner;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.impl.domain.common.ReflectionHelper;
import org.solverforge.wasm.service.SolverResource;

import org.jspecify.annotations.NonNull;

import com.dylibso.chicory.runtime.Instance;

public class WasmSolutionCloner implements SolutionCloner<WasmObject> {
    public static Cleaner solutionCleaner = Cleaner.create();

    @Override
    public @NonNull WasmObject cloneSolution(@NonNull WasmObject original) {
        var serialized = original.toString();
        var allocator = SolverResource.ALLOCATOR.get();
        var wasmInstance = SolverResource.INSTANCE.get();

        // Clear entity and list caches to ensure fresh objects after cloning.
        // Without this, cached entities from the original solution would be returned
        // with stale shadow variable state, causing "Unexpected unassigned position" errors.
        WasmObject.clearCacheForInstance(wasmInstance);
        WasmList.clearCacheForInstance(wasmInstance);

        try {
            var solutionClass = original.getClass();
            var constructor = solutionClass.getConstructor(Allocator.class, Instance.class, String.class);
            var out = constructor.newInstance(allocator, wasmInstance, serialized);
            var outMemoryLocation = out.getMemoryPointer();
            solutionCleaner.register(out, () -> {
                allocator.freeSolution(outMemoryLocation);
            });

            for (var method : solutionClass.getMethods()) {
                if (method.getAnnotation(PlanningScore.class) != null) {
                    var propertyName = method.getName().substring(3);
                    propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
                    ReflectionHelper.getSetterMethod(solutionClass, propertyName).invoke(out, method.invoke(original));
                    return out;
                }
            }
            throw new IllegalStateException("Impossible state: solution class does not have a PlanningScore annotation");
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
