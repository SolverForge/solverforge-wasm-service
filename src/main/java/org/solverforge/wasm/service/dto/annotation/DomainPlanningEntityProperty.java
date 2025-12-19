package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityProperty;

public final class DomainPlanningEntityProperty implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningEntityProperty.class;
    }
}
