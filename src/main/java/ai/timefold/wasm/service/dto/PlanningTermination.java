package ai.timefold.wasm.service.dto;

import ai.timefold.solver.core.config.solver.termination.DiminishedReturnsTerminationConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import io.quarkus.runtime.configuration.DurationConverter;

public record PlanningTermination(String spentLimit,
                                  String unimprovedSpentLimit,
                                  Integer unimprovedStepCount,
                                  String bestScoreLimit,
                                  Boolean bestScoreFeasible,
                                  Integer stepCountLimit,
                                  Long moveCountLimit,
                                  Long scoreCalculationCountLimit,
                                  PlanningDiminishedReturns diminishedReturns) {
    public PlanningTermination() {
        this(null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public PlanningTermination withSpentLimit(String spentLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withUnimprovedSpentLimit(String unimprovedSpentLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withUnimprovedSpentLimit(int unimprovedStepCount) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withBestScoreLimit(String bestScoreLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withBestScoreFeasible(boolean bestScoreFeasible) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withStepCountLimit(int stepCountLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withMoveCountLimit(long moveCountLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withScoreCalculationCountLimit(long scoreCalculationCountLimit) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public PlanningTermination withDiminishedReturns(PlanningDiminishedReturns diminishedReturns) {
        return new PlanningTermination(spentLimit,
                unimprovedSpentLimit,
                unimprovedStepCount,
                bestScoreLimit,
                bestScoreFeasible,
                stepCountLimit,
                moveCountLimit,
                scoreCalculationCountLimit,
                diminishedReturns);
    }

    public TerminationConfig asTerminationConfig() {
        var out = new TerminationConfig();
        if (spentLimit != null) {
            out.withSpentLimit(DurationConverter.parseDuration(spentLimit));
        }
        if (unimprovedSpentLimit != null) {
            out.withUnimprovedSpentLimit(DurationConverter.parseDuration(unimprovedSpentLimit));
        }
        if (unimprovedStepCount != null) {
            out.withUnimprovedStepCountLimit(unimprovedStepCount);
        }
        if (bestScoreLimit != null) {
            out.withBestScoreLimit(bestScoreLimit);
        }
        if (bestScoreFeasible != null) {
            out.withBestScoreFeasible(bestScoreFeasible);
        }
        if (diminishedReturns != null) {
            out.setDiminishedReturnsConfig(diminishedReturns.asDiminishedReturnsConfig());
        }
        if (stepCountLimit != null) {
            out.setStepCountLimit(stepCountLimit);
        }
        if (moveCountLimit != null) {
            out.withMoveCountLimit(moveCountLimit);
        }
        if (scoreCalculationCountLimit != null) {
            out.withScoreCalculationCountLimit(scoreCalculationCountLimit);
        }
        return out;
    }
}
