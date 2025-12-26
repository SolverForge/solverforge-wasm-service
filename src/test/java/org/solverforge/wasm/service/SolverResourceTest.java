package org.solverforge.wasm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SolverResourceTest {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    SolverResource solverResource;

    @Test
    public void solveTest() throws JsonProcessingException {
        var planningProblem = TestUtils.getPlanningProblem();
        var out = solverResource.solve(planningProblem);
        var solution = (Map) objectMapper.readerFor(Map.class).readValue(out.solution());
        assertThat(solution).containsKeys("employees", "shifts");
        assertThat(solution.get("shifts")).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(List.of(
                Map.of("employee", Map.of("id", 0)), Map.of("employee", Map.of("id", 1))
        ));
        assertThat(out.score()).isEqualTo(SimpleScore.of(18));
    }

    @Test
    public void analyseTest() {
        var planningProblem = TestUtils.getPlanningProblem();
        planningProblem.setProblem("""
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{}, {}]}
                """);
        var analysis = solverResource.analyze(planningProblem);
        assertThat(analysis.score()).isEqualTo(SimpleScore.ZERO);

        planningProblem.setProblem("""
                {"employees": [{"id": 0}, {"id": 1}], "shifts": [{"employee": {"id": 0}}, {"employee": {"id": 1}}]}
                """);
        analysis = solverResource.analyze(planningProblem);
        assertThat(analysis.score()).isEqualTo(SimpleScore.of(18));

        var constraintAnalysis = analysis.getConstraintAnalysis("penalizeId0");
        assertThat(constraintAnalysis).isNotNull();
        assertThat(constraintAnalysis.matchCount()).isEqualTo(2);
        assertThat(constraintAnalysis.score()).isEqualTo(SimpleScore.of(-2));

        constraintAnalysis = analysis.getConstraintAnalysis("distinctIds");
        assertThat(constraintAnalysis).isNotNull();
        assertThat(constraintAnalysis.matchCount()).isEqualTo(1);
        assertThat(constraintAnalysis.score()).isEqualTo(SimpleScore.of(20));

    }

    @Test
    public void asyncSolveTest() throws Exception {
        var planningProblem = TestUtils.getPlanningProblem();

        // Start async solve
        var asyncResponse = solverResource.solveAsync(planningProblem);
        assertThat(asyncResponse.solveId()).isNotNull();
        String solveId = asyncResponse.solveId();

        // Check status - should be running or already terminated
        var statusResponse = solverResource.getSolveStatus(solveId);
        assertThat(statusResponse.state()).isIn("RUNNING", "TERMINATED");

        // Wait for solve to complete (max 10 seconds)
        for (int i = 0; i < 100 && "RUNNING".equals(statusResponse.state()); i++) {
            Thread.sleep(100);
            statusResponse = solverResource.getSolveStatus(solveId);
        }

        // Should now be terminated
        assertThat(statusResponse.state()).isEqualTo("TERMINATED");
        assertThat(statusResponse.bestScore()).isNotNull();

        // Get best solution
        var bestResponse = solverResource.getBestSolution(solveId);
        assertThat(bestResponse.solution()).isNotNull();
        assertThat(bestResponse.score()).isNotNull();

        // Parse and verify solution
        var solution = (Map) objectMapper.readerFor(Map.class).readValue(bestResponse.solution());
        assertThat(solution).containsKeys("employees", "shifts");

        // Clean up
        solverResource.deleteSolve(solveId);
    }

    @Test
    public void asyncSolveStopTest() throws Exception {
        // Use a problem that takes longer to solve - increase termination time
        var planningProblem = TestUtils.getPlanningProblem();

        // Start async solve
        var asyncResponse = solverResource.solveAsync(planningProblem);
        String solveId = asyncResponse.solveId();

        // Small delay to let solve start
        Thread.sleep(50);

        // Stop the solve
        solverResource.stopSolve(solveId);

        // Wait for termination
        Thread.sleep(100);

        // Should be terminated now
        var statusResponse = solverResource.getSolveStatus(solveId);
        assertThat(statusResponse.state()).isEqualTo("TERMINATED");

        // Clean up
        solverResource.deleteSolve(solveId);
    }

    @Test
    public void asyncSolveNotFoundTest() {
        // Test with non-existent solve ID
        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.NotFoundException.class,
            () -> solverResource.getSolveStatus("non-existent-id")
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.NotFoundException.class,
            () -> solverResource.getBestSolution("non-existent-id")
        );

        org.junit.jupiter.api.Assertions.assertThrows(
            jakarta.ws.rs.NotFoundException.class,
            () -> solverResource.stopSolve("non-existent-id")
        );
    }
}
