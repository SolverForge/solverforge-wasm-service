package ai.timefold.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.wasm.service.Employee;
import ai.timefold.wasm.service.Schedule;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.TestUtils;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.GroupByComponent;
import ai.timefold.wasm.service.dto.constraint.IfExistsComponent;
import ai.timefold.wasm.service.dto.constraint.IfNotExistsComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;
import ai.timefold.wasm.service.dto.constraint.groupby.CountAggregator;
import ai.timefold.wasm.service.dto.constraint.joiner.GreaterThanJoiner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ComponentTest {
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

    @Test
    public void ifExists() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("thereABetterEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new IfExistsComponent("Employee", List.of(
                                new GreaterThanJoiner(new WasmFunction("getEmployeeId"),
                                        null, null, new WasmFunction("compareInt"))
                        )),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(0));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereABetterEmployee").score())
                .isEqualTo(SimpleScore.of(2));
    }

    @Test
    public void ifNotExists() throws JsonProcessingException {
        var problem = TestUtils.getPlanningProblem();
        problem.setConstraints(
                Map.of("thereNotABetterEmployee", new WasmConstraint(List.of(
                        new ForEachComponent("Employee"),
                        new IfNotExistsComponent("Employee", List.of(
                                new GreaterThanJoiner(new WasmFunction("getEmployeeId"),
                                        null, null, new WasmFunction("compareInt"))
                        )),
                        new RewardComponent("1", null)
                )))
        );

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1), Collections.emptyList()
        )));

        var analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));

        problem.setProblem(objectMapper.writeValueAsString(new Schedule(
                List.of(e1, e2, e3), Collections.emptyList()
        )));

        analysis = solverResource.analyze(problem);
        assertThat(analysis.getConstraintAnalysis("thereNotABetterEmployee").score())
                .isEqualTo(SimpleScore.of(1));
    }
}
