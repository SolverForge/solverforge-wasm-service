package org.solverforge.wasm.service.dto;

import ai.timefold.solver.core.config.solver.termination.DiminishedReturnsTerminationConfig;

import io.quarkus.runtime.configuration.DurationConverter;

public record PlanningDiminishedReturns(String slidingWindowDuration, Double minimumImprovementRatio) {
    public PlanningDiminishedReturns() {
        this(null, null);
    }

    public PlanningDiminishedReturns withSlidingWindowDuration(String slidingWindowDuration) {
        return new PlanningDiminishedReturns(slidingWindowDuration, minimumImprovementRatio);
    }

    public PlanningDiminishedReturns withMinimumImprovementRatio(double minimumImprovementRatio) {
        return new PlanningDiminishedReturns(slidingWindowDuration, minimumImprovementRatio);
    }

    public DiminishedReturnsTerminationConfig asDiminishedReturnsConfig() {
        var out = new DiminishedReturnsTerminationConfig();
        if (slidingWindowDuration != null) {
            out.withSlidingWindowDuration(DurationConverter.parseDuration(slidingWindowDuration));
        }
        if (minimumImprovementRatio != null) {
            out.withMinimumImprovementRatio(minimumImprovementRatio);
        }
        return out;
    }
}
