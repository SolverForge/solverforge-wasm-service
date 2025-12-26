package org.solverforge.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solverforge.wasm.service.SolverResource;
import org.solverforge.wasm.service.dto.DomainAccessor;
import org.solverforge.wasm.service.dto.DomainObject;
import org.solverforge.wasm.service.dto.FieldDescriptor;
import org.solverforge.wasm.service.dto.PlanningProblem;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningId;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningListVariable;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningScore;
import org.solverforge.wasm.service.dto.annotation.DomainValueRangeProvider;

/**
 * Tests for planning list variable element type resolution in DomainObjectClassGenerator.
 *
 * When a planning list variable references a value range provider, the generated
 * Java class should use the value range provider's element type (e.g., Visit) for
 * the list generic type, not the storage type (e.g., String).
 */
public class PlanningListVariableTest {

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

    /**
     * Tests that a planning list variable with valueRangeProviderRefs uses
     * the value range provider's element type in the generated generic signature.
     */
    @Test
    void planningListVariableUsesValueRangeProviderElementType() throws Exception {
        // Build a domain model like vehicle routing:
        // - Visit is a planning entity with an id
        // - Vehicle is a planning entity with a list variable referencing visits
        // - VehicleRoutePlan has visits as value range provider

        var visitFields = new LinkedHashMap<String, FieldDescriptor>();
        visitFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));

        var vehicleFields = new LinkedHashMap<String, FieldDescriptor>();
        vehicleFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        // List variable with String[] storage type but Visit as value range element type
        vehicleFields.put("visits", new FieldDescriptor("String[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningListVariable(false, new String[]{"visits"}))));

        var solutionFields = new LinkedHashMap<String, FieldDescriptor>();
        solutionFields.put("vehicles", new FieldDescriptor("Vehicle[]",
                new DomainAccessor("getVehicles", "setVehicles"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        // Value range provider with id "visits" returning Visit[]
        solutionFields.put("visits", new FieldDescriptor("Visit[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningEntityCollectionProperty(),
                        new DomainValueRangeProvider("visits"))));
        solutionFields.put("score", new FieldDescriptor("HardSoftScore",
                List.of(new DomainPlanningScore())));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Visit", new DomainObject(visitFields, null, null));
        domainObjects.put("Vehicle", new DomainObject(vehicleFields, null, null));
        domainObjects.put("VehicleRoutePlan", new DomainObject(solutionFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects,
                null, // constraints
                null, // environmentMode
                null, // wasmModule
                null, // allocatorFunctionName
                null, // deallocatorFunctionName
                null, // scoreHolderAccessor
                null, // listAccessor
                null, // solutionJson
                null, // termination
                null  // moveConfig
        );

        // Generate classes
        classGenerator.prepareClassesForPlanningProblem(planningProblem);

        // Get the generated Vehicle class
        Class<?> vehicleClass = classLoader.getClassForDomainClassName("Vehicle");
        Method getVisits = vehicleClass.getMethod("getVisits");

        // Verify the generic return type is WasmList<Visit>, not WasmList<String>
        assertThat(getVisits.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType returnType = (ParameterizedType) getVisits.getGenericReturnType();

        assertThat(returnType.getRawType()).isEqualTo(WasmList.class);
        assertThat(returnType.getActualTypeArguments()).hasSize(1);

        // The element type should be Visit, not String
        Class<?> elementType = (Class<?>) returnType.getActualTypeArguments()[0];
        assertThat(elementType.getSimpleName()).isEqualTo("Visit");
    }

    /**
     * Tests that looking up a non-existent value range provider throws an exception
     * instead of silently falling back to the storage type.
     */
    @Test
    void missingValueRangeProviderThrowsException() {
        var vehicleFields = new LinkedHashMap<String, FieldDescriptor>();
        vehicleFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        // List variable referencing a non-existent value range provider
        vehicleFields.put("visits", new FieldDescriptor("String[]",
                new DomainAccessor("getVisits", "setVisits"),
                List.of(new DomainPlanningListVariable(false, new String[]{"nonExistentProvider"}))));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Vehicle", new DomainObject(vehicleFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects,
                null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> classGenerator.prepareClassesForPlanningProblem(planningProblem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonExistentProvider")
                .hasMessageContaining("not found");
    }

    /**
     * Tests that a regular list (not a planning list variable) uses the storage type.
     */
    @Test
    void regularListUsesStorageType() throws Exception {
        var entityFields = new LinkedHashMap<String, FieldDescriptor>();
        entityFields.put("id", new FieldDescriptor("String",
                new DomainAccessor("getId", null),
                List.of(new DomainPlanningId())));
        // Regular list, not a planning list variable - but with an annotation so the field is processed
        entityFields.put("tags", new FieldDescriptor("String[]",
                new DomainAccessor("getTags", "setTags"),
                List.of(new DomainPlanningId()))); // Using any annotation to ensure field is processed

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Entity", new DomainObject(entityFields, null, null));

        var planningProblem = new PlanningProblem(
                domainObjects,
                null, null, null, null, null, null, null, null, null, null
        );

        classGenerator.prepareClassesForPlanningProblem(planningProblem);

        Class<?> entityClass = classLoader.getClassForDomainClassName("Entity");
        Method getTags = entityClass.getMethod("getTags");

        // Verify the generic return type is WasmList<String>
        assertThat(getTags.getGenericReturnType()).isInstanceOf(ParameterizedType.class);
        ParameterizedType returnType = (ParameterizedType) getTags.getGenericReturnType();

        assertThat(returnType.getRawType()).isEqualTo(WasmList.class);
        assertThat(returnType.getActualTypeArguments()).hasSize(1);

        // The element type should be String (storage type)
        Class<?> elementType = (Class<?>) returnType.getActualTypeArguments()[0];
        assertThat(elementType).isEqualTo(String.class);
    }
}
