package org.solverforge.wasm.service;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.analysis.ScoreAnalysis;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.util.MutableReference;
import org.solverforge.wasm.service.classgen.Allocator;
import org.solverforge.wasm.service.classgen.ConstraintProviderClassGenerator;
import org.solverforge.wasm.service.classgen.DomainObjectClassGenerator;
import org.solverforge.wasm.service.classgen.DomainObjectClassLoader;
import org.solverforge.wasm.service.classgen.WasmListAccessor;
import org.solverforge.wasm.service.dto.PlanningProblem;
import org.solverforge.wasm.service.dto.SolveResult;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.dylibso.chicory.compiler.MachineFactoryCompiler;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.WasmModule;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/")
public class SolverResource {
    private static final Logger LOG = Logger.getLogger(SolverResource.class);

    // Cache parsed WASM modules by SHA-256 hash to avoid re-parsing
    private static final ConcurrentHashMap<String, WasmModule> MODULE_CACHE = new ConcurrentHashMap<>();

    // Storage for active async solves
    private static final ConcurrentHashMap<String, ActiveSolve> ACTIVE_SOLVES = new ConcurrentHashMap<>();

    // Record for tracking an active solve
    record ActiveSolve(
        SolverManager<Object, String> solverManager,
        String bestSolution,
        Score<?> bestScore,
        SolverStatus status
    ) {
        ActiveSolve withBest(String solution, Score<?> score) {
            return new ActiveSolve(solverManager, solution, score, status);
        }
        ActiveSolve withStatus(SolverStatus status) {
            return new ActiveSolve(solverManager, bestSolution, bestScore, status);
        }
    }

    // DTO records for async API responses (field names must match Rust client's camelCase expectations)
    public record AsyncSolveResponse(String solveId) {}
    public record SolveStatusResponse(
        String state,
        long timeSpentMs,
        Object bestScore,  // ScoreDto or null
        String error
    ) {}
    public record BestSolutionResponse(String solution, Object score) {}

    public static ThreadLocal<Instance> INSTANCE = new ThreadLocal<>();
    public static ThreadLocal<ExportCache> EXPORT_CACHE = new ThreadLocal<>();
    public static ThreadLocal<FunctionCache> FUNCTION_CACHE = new ThreadLocal<>();
    public static ThreadLocal<WasmListAccessor> LIST_ACCESSOR = new ThreadLocal<>();
    public static ThreadLocal<Allocator> ALLOCATOR = new ThreadLocal<>();
    public static ThreadLocal<DomainObjectClassLoader> GENERATED_CLASS_LOADER = new ThreadLocal<>();

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "generatedClassPath", defaultValue = "")
    Optional<String> generatedClassPath;

    /**
     * Compute SHA-256 hash of WASM bytes for cache key.
     */
    private static String computeWasmHash(byte[] wasmBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(wasmBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Get or parse WASM module, using cache to avoid re-parsing.
     */
    private static WasmModule getOrParseModule(byte[] wasmBytes) {
        String hash = computeWasmHash(wasmBytes);
        return MODULE_CACHE.computeIfAbsent(hash, k -> {
            LOG.infof("Parsing new WASM module (hash=%s, size=%d bytes)", hash.substring(0, 16), wasmBytes.length);
            return Parser.parse(wasmBytes);
        });
    }

    private Instance createWasmInstance(PlanningProblem planningProblem) {
        var hostFunctions = new HostFunctionProvider(objectMapper, planningProblem).createHostFunctions();

        // Use cached WASM module to avoid re-parsing
        var module = getOrParseModule(planningProblem.getWasm());

        var instanceBuilder = Instance.builder(module)
                .withMemoryFactory(ByteArrayMemory::new)
                .withMachineFactory(MachineFactoryCompiler::compile);

        var optionsBuilder = WasiOptions.builder()
                .inheritSystem();

        for (var environmentEntry : System.getenv().entrySet()) {
            optionsBuilder.withEnvironment(environmentEntry.getKey(), environmentEntry.getValue());
        }

        var options = optionsBuilder.build();
        // create our instance of wasip1
        var wasi = WasiPreview1.builder().withOptions(options).build();

        var importFunctions = hostFunctions.toArray(new ImportFunction[0]);
        instanceBuilder.withImportValues(ImportValues.builder()
                .addFunction(importFunctions)
                .addFunction(wasi.toHostFunctions())
                .build());

        var out = instanceBuilder.build();
        out.initialize(true);
        return out;
    }

    private Object convertPlanningProblem(Instance wasmInstance, DomainObjectClassLoader classLoader, PlanningProblem planningProblem) {
        var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
        var allocator = ALLOCATOR.get();
        try {
            return solutionClass.getConstructor(Allocator.class, Instance.class, String.class).newInstance(allocator, wasmInstance, planningProblem.getProblem());
        } catch (InvocationTargetException e) {
            // Extract the actual cause from the reflection wrapper
            throw new RuntimeException("Failed to construct solution: " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T usingGeneratedSolverAndPlanningProblem(PlanningProblem planningProblem, BiFunction<Object, SolverFactory<Object>, T> resultFunction) {
        var solverConfig = new SolverConfig();

        var domainObjectClassGenerator = new DomainObjectClassGenerator();
        var wasmInstance = createWasmInstance(planningProblem);

        try {
            var classLoader = new DomainObjectClassLoader();
            GENERATED_CLASS_LOADER.set(classLoader);
            INSTANCE.set(wasmInstance);
            EXPORT_CACHE.set(new ExportCache(wasmInstance));
            FUNCTION_CACHE.set(new FunctionCache());
            LIST_ACCESSOR.set(new WasmListAccessor(wasmInstance, planningProblem.getListAccessor()));
            ALLOCATOR.set(new Allocator(wasmInstance, planningProblem.getAllocator(), planningProblem.getDeallocator(),
                    planningProblem.getSolutionDeallocator()));

            domainObjectClassGenerator.prepareClassesForPlanningProblem(planningProblem);

            var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
            var entityClassList = new ArrayList<Class<?>>(planningProblem.getEntityClassList().size());
            for (var entityClass : planningProblem.getEntityClassList()) {
                entityClassList.add(classLoader.getClassForDomainClassName(entityClass));
            }

            solverConfig.setSolutionClass(solutionClass);
            solverConfig.setEntityClassList(entityClassList);
            solverConfig.setEnvironmentMode(planningProblem.getEnvironmentMode());

            var constraintProviderClass = new ConstraintProviderClassGenerator()
                    .defineConstraintProviderClass(planningProblem);

            generatedClassPath.ifPresent(s -> classLoader.dumpGeneratedClasses(Paths.get(s)));

            solverConfig.withConstraintProviderClass(constraintProviderClass);

            solverConfig.withTerminationConfig(planningProblem.terminationConfig());

            var solverFactory = SolverFactory.create(solverConfig);
            var solverInput = convertPlanningProblem(wasmInstance, classLoader, planningProblem);

            return resultFunction.apply(solverInput, solverFactory);
        } finally {
            GENERATED_CLASS_LOADER.remove();
            LIST_ACCESSOR.remove();
            FUNCTION_CACHE.remove();
            EXPORT_CACHE.remove();
            INSTANCE.remove();
            ALLOCATOR.remove();
        }
    }

    @POST
    @Path("solve")
    public SolveResult solve(PlanningProblem planningProblem) {
        return usingGeneratedSolverAndPlanningProblem(planningProblem, (solverInput, solverFactory) -> {
            var solver = solverFactory.buildSolver();

            // Copy the solution into a map; we don't know enough from the WASM
            // to create an accurate planning clone that cannot be corrupted by
            // constraints/setters
            MutableReference<SolveResult> bestSolutionRef = new MutableReference<>(
                    new SolveResult(planningProblem.getProblem(), null, null));
            solver.addEventListener(event -> {
                bestSolutionRef.setValue(new SolveResult(event.getNewBestSolution().toString(), event.getNewBestScore(), null));
            });

            solver.solve(solverInput);

            // Extract metrics from DefaultSolver
            var result = bestSolutionRef.getValue();
            var defaultSolver = (ai.timefold.solver.core.impl.solver.DefaultSolver<?>) solver;
            var stats = new org.solverforge.wasm.service.dto.SolverStats(
                    defaultSolver.getTimeMillisSpent(),
                    defaultSolver.getScoreCalculationCount(),
                    defaultSolver.getScoreCalculationSpeed(),
                    defaultSolver.getMoveEvaluationCount(),
                    defaultSolver.getMoveEvaluationSpeed());
            return new SolveResult(result.solution(), result.score(), stats);
        });
    }

    @POST
    @Path("analyze")
    public ScoreAnalysis<?> analyze(PlanningProblem planningProblem) {
        return usingGeneratedSolverAndPlanningProblem(planningProblem, (solverInput, solverFactory) -> {
            var solutionManager = SolutionManager.create(SolverManager.create(solverFactory));
            return solutionManager.analyze(solverInput);
        });
    }

    // ============================================================================
    // Async Solving Endpoints
    // ============================================================================

    // Executor for background solves
    private static final java.util.concurrent.ExecutorService SOLVE_EXECUTOR =
        java.util.concurrent.Executors.newCachedThreadPool();

    // Active async solve context (keeps WASM instance and class loader alive)
    record AsyncSolveContext(
        Instance wasmInstance,
        DomainObjectClassLoader classLoader,
        WasmListAccessor listAccessor,
        ExportCache exportCache,
        FunctionCache functionCache,
        Allocator allocator,
        ai.timefold.solver.core.api.solver.Solver<Object> solver,
        MutableReference<String> bestSolution,
        MutableReference<Score<?>> bestScore,
        java.util.concurrent.atomic.AtomicBoolean solving
    ) {}

    private static final ConcurrentHashMap<String, AsyncSolveContext> ASYNC_CONTEXTS = new ConcurrentHashMap<>();

    @POST
    @Path("solve/async")
    public AsyncSolveResponse solveAsync(PlanningProblem planningProblem) {
        String solveId = UUID.randomUUID().toString();

        // Set up the WASM context
        var domainObjectClassGenerator = new DomainObjectClassGenerator();
        var wasmInstance = createWasmInstance(planningProblem);
        var classLoader = new DomainObjectClassLoader();
        var listAccessor = new WasmListAccessor(wasmInstance, planningProblem.getListAccessor());
        var exportCache = new ExportCache(wasmInstance);
        var functionCache = new FunctionCache();
        var allocator = new Allocator(wasmInstance, planningProblem.getAllocator(), planningProblem.getDeallocator(),
                planningProblem.getSolutionDeallocator());

        // Set ThreadLocals for class generation
        GENERATED_CLASS_LOADER.set(classLoader);
        INSTANCE.set(wasmInstance);
        EXPORT_CACHE.set(exportCache);
        FUNCTION_CACHE.set(functionCache);
        LIST_ACCESSOR.set(listAccessor);
        ALLOCATOR.set(allocator);

        try {
            domainObjectClassGenerator.prepareClassesForPlanningProblem(planningProblem);

            var solutionClass = classLoader.getClassForDomainClassName(planningProblem.getSolutionClass());
            var entityClassList = new ArrayList<Class<?>>(planningProblem.getEntityClassList().size());
            for (var entityClass : planningProblem.getEntityClassList()) {
                entityClassList.add(classLoader.getClassForDomainClassName(entityClass));
            }

            var solverConfig = new SolverConfig();
            solverConfig.setSolutionClass(solutionClass);
            solverConfig.setEntityClassList(entityClassList);
            solverConfig.setEnvironmentMode(planningProblem.getEnvironmentMode());

            var constraintProviderClass = new ConstraintProviderClassGenerator()
                    .defineConstraintProviderClass(planningProblem);

            generatedClassPath.ifPresent(s -> classLoader.dumpGeneratedClasses(Paths.get(s)));

            solverConfig.withConstraintProviderClass(constraintProviderClass);
            solverConfig.withTerminationConfig(planningProblem.terminationConfig());

            var solverFactory = SolverFactory.create(solverConfig);
            var solver = solverFactory.buildSolver();

            // Convert the problem
            var solverInput = convertPlanningProblem(wasmInstance, classLoader, planningProblem);

            // Track best solution
            var bestSolutionRef = new MutableReference<String>(planningProblem.getProblem());
            var bestScoreRef = new MutableReference<Score<?>>(null);
            var solving = new java.util.concurrent.atomic.AtomicBoolean(true);

            solver.addEventListener(event -> {
                bestSolutionRef.setValue(event.getNewBestSolution().toString());
                bestScoreRef.setValue(event.getNewBestScore());
            });

            // Store context for later queries
            var context = new AsyncSolveContext(
                wasmInstance, classLoader, listAccessor, exportCache, functionCache,
                allocator, solver, bestSolutionRef, bestScoreRef, solving
            );
            ASYNC_CONTEXTS.put(solveId, context);

            // Run solve in background with proper ThreadLocal context
            SOLVE_EXECUTOR.submit(() -> {
                GENERATED_CLASS_LOADER.set(classLoader);
                INSTANCE.set(wasmInstance);
                EXPORT_CACHE.set(exportCache);
                FUNCTION_CACHE.set(functionCache);
                LIST_ACCESSOR.set(listAccessor);
                ALLOCATOR.set(allocator);
                try {
                    solver.solve(solverInput);
                } finally {
                    solving.set(false);
                    GENERATED_CLASS_LOADER.remove();
                    LIST_ACCESSOR.remove();
                    FUNCTION_CACHE.remove();
                    EXPORT_CACHE.remove();
                    INSTANCE.remove();
                    ALLOCATOR.remove();
                }
            });

            return new AsyncSolveResponse(solveId);
        } finally {
            // Clean up ThreadLocals from setup thread (context is preserved in ASYNC_CONTEXTS)
            GENERATED_CLASS_LOADER.remove();
            LIST_ACCESSOR.remove();
            FUNCTION_CACHE.remove();
            EXPORT_CACHE.remove();
            INSTANCE.remove();
            ALLOCATOR.remove();
        }
    }

    @GET
    @Path("solve/{id}/status")
    public SolveStatusResponse getSolveStatus(@PathParam("id") String solveId) {
        var context = ASYNC_CONTEXTS.get(solveId);
        if (context == null) {
            throw new jakarta.ws.rs.NotFoundException("Solve not found: " + solveId);
        }

        String state = context.solving().get() ? "RUNNING" : "TERMINATED";
        Score<?> bestScore = context.bestScore().getValue();

        return new SolveStatusResponse(state, 0L, bestScore, null);
    }

    @GET
    @Path("solve/{id}/best")
    public BestSolutionResponse getBestSolution(@PathParam("id") String solveId) {
        var context = ASYNC_CONTEXTS.get(solveId);
        if (context == null) {
            throw new jakarta.ws.rs.NotFoundException("Solve not found: " + solveId);
        }

        String solution = context.bestSolution().getValue();
        Score<?> score = context.bestScore().getValue();

        return new BestSolutionResponse(solution, score);
    }

    @POST
    @Path("solve/{id}/stop")
    public void stopSolve(@PathParam("id") String solveId) {
        var context = ASYNC_CONTEXTS.get(solveId);
        if (context == null) {
            throw new jakarta.ws.rs.NotFoundException("Solve not found: " + solveId);
        }

        context.solver().terminateEarly();
        // Note: context cleanup happens when solve thread exits
    }

    @DELETE
    @Path("solve/{id}")
    public void deleteSolve(@PathParam("id") String solveId) {
        var context = ASYNC_CONTEXTS.remove(solveId);
        if (context != null && context.solving().get()) {
            context.solver().terminateEarly();
        }
    }
}
