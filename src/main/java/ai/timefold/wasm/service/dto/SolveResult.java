package ai.timefold.wasm.service.dto;

import ai.timefold.solver.core.api.score.Score;

public record SolveResult(String solution, Score<?> score, SolverStats stats) {
}
