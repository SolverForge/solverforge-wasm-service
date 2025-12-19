package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class DomainPlanningListVariable implements PlanningAnnotation {
    boolean allowsUnassignedValues;

    @JsonCreator
    public DomainPlanningListVariable() {
        this.allowsUnassignedValues = false;
    }

    @JsonCreator
    public DomainPlanningListVariable(boolean allowsUnassignedValues) {
        this.allowsUnassignedValues = allowsUnassignedValues;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningListVariable.class;
    }

    @Override
    public boolean definesPlanningEntity() {
        return true;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        return List.of(
                AnnotationElement.of("allowsUnassignedValues", AnnotationValue.of(allowsUnassignedValues))
        );
    }
}
