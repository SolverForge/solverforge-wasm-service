package ai.timefold.wasm.service.dto;

import ai.timefold.solver.core.api.score.Score;

public record SolveResult<Score_ extends Score<Score_>>(String solution, Score_ score) {
}
