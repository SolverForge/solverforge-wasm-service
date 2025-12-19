package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;

import ai.timefold.solver.core.api.domain.entity.PlanningPin;

public final class DomainPlanningPin implements PlanningAnnotation {
    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningPin.class;
    }
}
