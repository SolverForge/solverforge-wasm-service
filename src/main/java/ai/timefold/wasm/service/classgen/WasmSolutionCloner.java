package ai.timefold.wasm.service.classgen;

import java.lang.reflect.InvocationTargetException;

import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.cloner.SolutionCloner;
import ai.timefold.solver.core.impl.domain.common.ReflectionHelper;
import ai.timefold.wasm.service.SolverResource;

import org.jspecify.annotations.NonNull;

import com.dylibso.chicory.runtime.Instance;

public class WasmSolutionCloner implements SolutionCloner<WasmObject> {
    @Override
    public @NonNull WasmObject cloneSolution(@NonNull WasmObject original) {
        var serialized = original.toString();
        try {
            var solutionClass = original.getClass();
            var constructor = solutionClass.getConstructor(Allocator.class, Instance.class, String.class);
            var out = constructor.newInstance(SolverResource.ALLOCATOR.get(), SolverResource.INSTANCE.get(), serialized);
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
