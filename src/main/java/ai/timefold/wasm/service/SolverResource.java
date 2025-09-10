package ai.timefold.wasm.service;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.impl.util.MutableReference;
import ai.timefold.wasm.service.classgen.Allocator;
import ai.timefold.wasm.service.classgen.ConstraintProviderClassGenerator;
import ai.timefold.wasm.service.classgen.DomainObjectClassGenerator;
import ai.timefold.wasm.service.classgen.DomainObjectClassLoader;
import ai.timefold.wasm.service.classgen.WasmListAccessor;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.SolveResult;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;

@Path("/solver")
public class SolverResource {
    public static ThreadLocal<Instance> INSTANCE = new ThreadLocal<>();
    public static ThreadLocal<WasmListAccessor> LIST_ACCESSOR = new ThreadLocal<>();
    public static ThreadLocal<Allocator> ALLOCATOR = new ThreadLocal<>();
    public static ThreadLocal<DomainObjectClassLoader> GENERATED_CLASS_LOADER = new ThreadLocal<>();

    private List<HostFunction> hostFunctionList = Collections.emptyList();

    public void setHostFunctionList(List<HostFunction> hostFunctionList) {
        this.hostFunctionList = hostFunctionList;
    }

    @POST
    public SolveResult<?> solve(PlanningProblem planningProblem) {
        var solverConfig = new SolverConfig();

        var domainObjectClassGenerator = new DomainObjectClassGenerator();
        var wasmInstance = createWasmInstance(planningProblem.getWasm());

        try {
            var classLoader = new DomainObjectClassLoader();
            GENERATED_CLASS_LOADER.set(classLoader);
            INSTANCE.set(wasmInstance);
            LIST_ACCESSOR.set(new WasmListAccessor(wasmInstance, planningProblem.getListAccessor()));
            ALLOCATOR.set(new Allocator(wasmInstance, planningProblem.getAllocator(), planningProblem.getDeallocator()));

            domainObjectClassGenerator.prepareClassesForPlanningProblem(planningProblem);

            var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
            var entityClassList = new ArrayList<Class<?>>(planningProblem.getEntityClassList().size());
            for (var entityClass : planningProblem.getEntityClassList()) {
                entityClassList.add(classLoader.getClassForDomainClassName(entityClass));
            }

            solverConfig.setSolutionClass(solutionClass);
            solverConfig.setEntityClassList(entityClassList);

            var constraintProviderClass = new ConstraintProviderClassGenerator(domainObjectClassGenerator, wasmInstance)
                    .defineConstraintProviderClass(planningProblem);
            solverConfig.withConstraintProviderClass(constraintProviderClass);

            solverConfig.withTerminationConfig(new TerminationConfig().withSecondsSpentLimit(1L));

            var solverFactory = SolverFactory.create(solverConfig);
            var solver = solverFactory.buildSolver();
            var solverInput = convertPlanningProblem(wasmInstance, classLoader, planningProblem);

            // Copy the solution into a map; we don't know enough from the WASM
            // to create an accurate planning clone that cannot be corrupted by
            // constraints/setters
            MutableReference<SolveResult<?>> bestSolutionRef = new MutableReference<>(
                    new SolveResult<>(planningProblem.getProblem(), null));
            solver.addEventListener(event -> {
                bestSolutionRef.setValue(new SolveResult<>(event.getNewBestSolution().toString(), event.getNewBestScore()));
            });

            solver.solve(solverInput);
            return bestSolutionRef.getValue();
        } finally {
            GENERATED_CLASS_LOADER.remove();
            LIST_ACCESSOR.remove();
        }
    }

    private Instance createWasmInstance(byte[] wasm) {
        var instanceBuilder = Instance.builder(Parser.parse(wasm))
                .withMemoryFactory(ByteArrayMemory::new)
                .withMachineFactory(MachineFactoryCompiler::compile);

        if (!hostFunctionList.isEmpty()) {
            var importFunctions = new ImportFunction[hostFunctionList.size()];
            for (int i = 0; i < importFunctions.length; i++) {
                importFunctions[i] = hostFunctionList.get(i);
            }
            instanceBuilder.withImportValues(ImportValues.builder()
                    .addFunction(importFunctions).build());
        }
        var out = instanceBuilder.build();
        out.initialize(true);
        return out;
    }

    private Object convertPlanningProblem(Instance wasmInstance, DomainObjectClassLoader classLoader, PlanningProblem planningProblem) {
        var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
        var allocator = ALLOCATOR.get();
        try {
            return solutionClass.getConstructor(Allocator.class, Instance.class, String.class).newInstance(allocator, wasmInstance, planningProblem.getProblem());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
