package ai.timefold.wasm.service.dto;

/**
 * Performance statistics from a solver run.
 */
public record SolverStats(
        long timeSpentMillis,
        long scoreCalculationCount,
        long scoreCalculationSpeed,
        long moveEvaluationCount,
        long moveEvaluationSpeed) {
}
