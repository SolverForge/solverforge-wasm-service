package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;

public final class DomainProblemFactProperty implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return ProblemFactProperty.class;
    }
}
