package org.solverforge.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solverforge.wasm.service.SolverResource;
import org.solverforge.wasm.service.dto.DomainAccessor;
import org.solverforge.wasm.service.dto.DomainObject;
import org.solverforge.wasm.service.dto.FieldDescriptor;
import org.solverforge.wasm.service.dto.PlanningProblem;
import org.solverforge.wasm.service.dto.annotation.DomainCascadingUpdateShadowVariable;
import org.solverforge.wasm.service.dto.annotation.DomainInverseRelationShadowVariable;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningId;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningListVariable;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningScore;
import org.solverforge.wasm.service.dto.annotation.DomainPreviousElementShadowVariable;
import org.solverforge.wasm.service.dto.annotation.DomainValueRangeProvider;

/**
 * Tests that shadow variable annotations cause classes to be recognized as planning entities.
 *
 * Shadow variables (InverseRelationShadowVariable, PreviousElementShadowVariable,
 * CascadingUpdateShadowVariable, etc.) are only valid on planning entity classes.
 * If a class has shadow variable annotations but is not marked as @PlanningEntity,
 * Timefold will not discover the shadow variable descriptors and cascading updates
 * will not be triggered during local search.
 */
public class ShadowVariableEntityTest {

    private static final String MINIMAL_WASM = "AGFzbQEAAAA=";

    private DomainObjectClassGenerator classGenerator;
    private DomainObjectClassLoader classLoader;

    @BeforeEach
    void setUp() {
        classGenerator = new DomainObjectClassGenerator();
        classLoader = new DomainObjectClassLoader();
        SolverResource.GENERATED_CLASS_LOADER.set(classLoader);
    }

    @AfterEach
    void tearDown() {
        SolverResource.GENERATED_CLASS_LOADER.remove();
    }

    @Test
    void cascadingUpdateShadowVariableDefinesPlanningEntity() throws Exception {
        var visitFields = new LinkedHashMap<String, FieldDescriptor>();
        visitFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        visitFields.put("arrivalTime", new FieldDescriptor("long",
                new DomainAccessor("getArrivalTime", "setArrivalTime"),
                List.of(new DomainCascadingUpdateShadowVariable("updateArrivalTime"))));

        var vehicleFields = new LinkedHashMap<String, FieldDescriptor>();
        vehicleFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        vehicleFields.put("visits", new FieldDescriptor("String[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningListVariable(false, new String[]{"visits"}))));

        var solutionFields = new LinkedHashMap<String, FieldDescriptor>();
        solutionFields.put("vehicles", new FieldDescriptor("Vehicle[]",
                new DomainAccessor("getVehicles", "setVehicles"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        solutionFields.put("visits", new FieldDescriptor("Visit[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningEntityCollectionProperty(),
                        new DomainValueRangeProvider("visits"))));
        solutionFields.put("score", new FieldDescriptor("HardSoftScore",
                List.of(new DomainPlanningScore())));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Visit", new DomainObject(visitFields, null, null));
        domainObjects.put("Vehicle", new DomainObject(vehicleFields, null, null));
        domainObjects.put("Solution", new DomainObject(solutionFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects, null, null, MINIMAL_WASM,
                "alloc", "dealloc", null, null, "{}", null, null);

        classGenerator.prepareClassesForPlanningProblem(planningProblem);

        Class<?> visitClass = classLoader.getClassForDomainClassName("Visit");

        // Verify Visit is a @PlanningEntity
        assertThat(visitClass.isAnnotationPresent(PlanningEntity.class))
                .as("Class with @CascadingUpdateShadowVariable should be a @PlanningEntity")
                .isTrue();

        // Verify the @CascadingUpdateShadowVariable annotation is present on the getter
        var getArrivalTime = visitClass.getMethod("getArrivalTime");
        assertThat(getArrivalTime.isAnnotationPresent(CascadingUpdateShadowVariable.class))
                .as("Getter should have @CascadingUpdateShadowVariable annotation")
                .isTrue();

        // Verify the target method exists
        var updateArrivalTime = visitClass.getMethod("updateArrivalTime");
        assertThat(updateArrivalTime).isNotNull();
    }

    @Test
    void inverseRelationShadowVariableDefinesPlanningEntity() throws Exception {
        var visitFields = new LinkedHashMap<String, FieldDescriptor>();
        visitFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        visitFields.put("vehicle", new FieldDescriptor("Vehicle",
                new DomainAccessor("getVehicle", "setVehicle"),
                List.of(new DomainInverseRelationShadowVariable("visits"))));

        var vehicleFields = new LinkedHashMap<String, FieldDescriptor>();
        vehicleFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        vehicleFields.put("visits", new FieldDescriptor("String[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningListVariable(false, new String[]{"visits"}))));

        var solutionFields = new LinkedHashMap<String, FieldDescriptor>();
        solutionFields.put("vehicles", new FieldDescriptor("Vehicle[]",
                new DomainAccessor("getVehicles", "setVehicles"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        solutionFields.put("visits", new FieldDescriptor("Visit[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningEntityCollectionProperty(),
                        new DomainValueRangeProvider("visits"))));
        solutionFields.put("score", new FieldDescriptor("HardSoftScore",
                List.of(new DomainPlanningScore())));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Visit", new DomainObject(visitFields, null, null));
        domainObjects.put("Vehicle", new DomainObject(vehicleFields, null, null));
        domainObjects.put("Solution", new DomainObject(solutionFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects, null, null, MINIMAL_WASM,
                "alloc", "dealloc", null, null, "{}", null, null);

        classGenerator.prepareClassesForPlanningProblem(planningProblem);

        Class<?> visitClass = classLoader.getClassForDomainClassName("Visit");

        // Verify Visit is a @PlanningEntity
        assertThat(visitClass.isAnnotationPresent(PlanningEntity.class))
                .as("Class with @InverseRelationShadowVariable should be a @PlanningEntity")
                .isTrue();

        // Verify the @InverseRelationShadowVariable annotation is present
        var getVehicle = visitClass.getMethod("getVehicle");
        assertThat(getVehicle.isAnnotationPresent(InverseRelationShadowVariable.class))
                .as("Getter should have @InverseRelationShadowVariable annotation")
                .isTrue();
    }

    @Test
    void previousElementShadowVariableDefinesPlanningEntity() throws Exception {
        var visitFields = new LinkedHashMap<String, FieldDescriptor>();
        visitFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        visitFields.put("previousVisit", new FieldDescriptor("Visit",
                new DomainAccessor("getPreviousVisit", "setPreviousVisit"),
                List.of(new DomainPreviousElementShadowVariable("visits"))));

        var vehicleFields = new LinkedHashMap<String, FieldDescriptor>();
        vehicleFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        vehicleFields.put("visits", new FieldDescriptor("String[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningListVariable(false, new String[]{"visits"}))));

        var solutionFields = new LinkedHashMap<String, FieldDescriptor>();
        solutionFields.put("vehicles", new FieldDescriptor("Vehicle[]",
                new DomainAccessor("getVehicles", "setVehicles"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        solutionFields.put("visits", new FieldDescriptor("Visit[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningEntityCollectionProperty(),
                        new DomainValueRangeProvider("visits"))));
        solutionFields.put("score", new FieldDescriptor("HardSoftScore",
                List.of(new DomainPlanningScore())));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Visit", new DomainObject(visitFields, null, null));
        domainObjects.put("Vehicle", new DomainObject(vehicleFields, null, null));
        domainObjects.put("Solution", new DomainObject(solutionFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects, null, null, MINIMAL_WASM,
                "alloc", "dealloc", null, null, "{}", null, null);

        classGenerator.prepareClassesForPlanningProblem(planningProblem);

        Class<?> visitClass = classLoader.getClassForDomainClassName("Visit");

        // Verify Visit is a @PlanningEntity
        assertThat(visitClass.isAnnotationPresent(PlanningEntity.class))
                .as("Class with @PreviousElementShadowVariable should be a @PlanningEntity")
                .isTrue();

        // Verify the @PreviousElementShadowVariable annotation is present
        var getPreviousVisit = visitClass.getMethod("getPreviousVisit");
        assertThat(getPreviousVisit.isAnnotationPresent(PreviousElementShadowVariable.class))
                .as("Getter should have @PreviousElementShadowVariable annotation")
                .isTrue();
    }
}
