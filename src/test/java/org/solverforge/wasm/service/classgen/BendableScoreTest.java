package org.solverforge.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.bendable.BendableScore;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import org.solverforge.wasm.service.Employee;
import org.solverforge.wasm.service.Schedule;
import org.solverforge.wasm.service.Shift;
import org.solverforge.wasm.service.SolverResource;
import org.solverforge.wasm.service.TestUtils;
import org.solverforge.wasm.service.dto.DomainAccessor;
import org.solverforge.wasm.service.dto.DomainListAccessor;
import org.solverforge.wasm.service.dto.DomainObject;
import org.solverforge.wasm.service.dto.DomainObjectMapper;
import org.solverforge.wasm.service.dto.FieldDescriptor;
import org.solverforge.wasm.service.dto.PlanningProblem;
import org.solverforge.wasm.service.dto.PlanningTermination;
import org.solverforge.wasm.service.dto.WasmConstraint;
import org.solverforge.wasm.service.dto.WasmFunction;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningEntityCollectionProperty;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningId;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningScore;
import org.solverforge.wasm.service.dto.annotation.DomainPlanningVariable;
import org.solverforge.wasm.service.dto.annotation.DomainProblemFactCollectionProperty;
import org.solverforge.wasm.service.dto.annotation.DomainValueRangeProvider;
import org.solverforge.wasm.service.dto.constraint.ForEachComponent;
import org.solverforge.wasm.service.dto.constraint.GroupByComponent;
import org.solverforge.wasm.service.dto.constraint.PenalizeComponent;
import org.solverforge.wasm.service.dto.constraint.RewardComponent;
import org.solverforge.wasm.service.dto.constraint.groupby.CountAggregator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dylibso.chicory.wabt.Wat2Wasm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class BendableScoreTest {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverResource solverResource;

    Employee e0, e1, e2, e3;

    @BeforeEach
    public void setup() {
        TestUtils.setup(solverResource, objectMapper);
        e0 = new Employee("0");
        e1 = new Employee("1");
        e2 = new Employee("2");
        e3 = new Employee("3");
    }

    private PlanningProblem getBendableScorePlanningProblem(int hardLevels, int softLevels) {
        var employeeFields = new LinkedHashMap<String, FieldDescriptor>();
        employeeFields.put("id", new FieldDescriptor("int",
                new DomainAccessor("getEmployeeId", null),
                List.of(new DomainPlanningId())));

        var shiftFields = new LinkedHashMap<String, FieldDescriptor>();
        shiftFields.put("employee", new FieldDescriptor("Employee",
                new DomainAccessor("getEmployee", "setEmployee"),
                List.of(new DomainPlanningVariable(false, null))));

        var scheduleFields = new LinkedHashMap<String, FieldDescriptor>();
        scheduleFields.put("employees", new FieldDescriptor("Employee[]",
                new DomainAccessor("getEmployees", "setEmployees"),
                List.of(new DomainProblemFactCollectionProperty(), new DomainValueRangeProvider())));
        scheduleFields.put("shifts", new FieldDescriptor("Shift[]",
                new DomainAccessor("getShifts", "setShifts"),
                List.of(new DomainPlanningEntityCollectionProperty())));
        scheduleFields.put("score", new FieldDescriptor("BendableScore",
                List.of(new DomainPlanningScore(hardLevels, softLevels))));

        var domainObjects = new LinkedHashMap<String, DomainObject>();
        domainObjects.put("Employee", new DomainObject(employeeFields, null, null));
        domainObjects.put("Shift", new DomainObject(shiftFields, null, null));
        domainObjects.put("Schedule", new DomainObject(scheduleFields,
                new DomainObjectMapper("parseSchedule", "scheduleString"), null));

        // Get the base WASM module from TestUtils and reuse it
        var baseProblem = TestUtils.getPlanningProblem();

        return new PlanningProblem(
                domainObjects,
                Map.of(),  // constraints will be set per test
                EnvironmentMode.FULL_ASSERT,
                Base64.getEncoder().encodeToString(baseProblem.getWasm()),
                "alloc",
                "dealloc",
                null,
                new DomainListAccessor(
                        "newList", "getItem", "setItem", "size",
                        "append", "insert", "remove", "dealloc"
                ),
                """
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{}, {}]}
                """,
                new PlanningTermination(null, null, null, null, null, 10, null, null, null),
                null
        );
    }

    @Test
    public void testBendableScoreWithTwoHardOneSoft() throws JsonProcessingException {
        var problem = getBendableScorePlanningProblem(2, 1);
        problem.setConstraints(
                Map.of("countEmployees", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new GroupByComponent(Collections.emptyList(),
                                List.of(new CountAggregator(false, null))),
                        new RewardComponent("[0/0]hard/[1]soft", new WasmFunction("scaleByCount"))
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("countEmployees").score())
                .isEqualTo(BendableScore.of(new int[]{0, 0}, new int[]{2}));
    }

    @Test
    public void testBendableScoreHardConstraint() throws JsonProcessingException {
        var problem = getBendableScorePlanningProblem(2, 1);
        problem.setConstraints(
                Map.of("hardConstraint", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new PenalizeComponent("[1/0]hard/[0]soft", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        // 3 employees, each penalized by [1/0]hard/[0]soft
        assertThat(analysis.getConstraintAnalysis("hardConstraint").score())
                .isEqualTo(BendableScore.of(new int[]{-3, 0}, new int[]{0}));
    }

    @Test
    public void testBendableScoreMultipleLevels() throws JsonProcessingException {
        var problem = getBendableScorePlanningProblem(3, 2);
        problem.setConstraints(
                Map.of(
                        "level0Hard", new WasmConstraint(List.of(
                                new ForEachComponent("Employee"),
                                new PenalizeComponent("[1/0/0]hard/[0/0]soft", null)
                        )),
                        "level1Soft", new WasmConstraint(List.of(
                                new ForEachComponent("Employee"),
                                new RewardComponent("[0/0/0]hard/[0/1]soft", null)
                        ))
                )
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);

        assertThat(analysis.getConstraintAnalysis("level0Hard").score())
                .isEqualTo(BendableScore.of(new int[]{-2, 0, 0}, new int[]{0, 0}));

        assertThat(analysis.getConstraintAnalysis("level1Soft").score())
                .isEqualTo(BendableScore.of(new int[]{0, 0, 0}, new int[]{0, 2}));
    }

    @Test
    public void testBendableScoreFeasibility() throws JsonProcessingException {
        var problem = getBendableScorePlanningProblem(1, 1);
        problem.setConstraints(
                Map.of("feasible", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new RewardComponent("[0]hard/[1]soft", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        var score = (BendableScore) analysis.getConstraintAnalysis("feasible").score();

        // Score is [0]hard/[1]soft - feasible because hard >= 0
        assertThat(score.isFeasible()).isTrue();
        assertThat(score.hardScore(0)).isEqualTo(0);
        assertThat(score.softScore(0)).isEqualTo(1);
    }
}
