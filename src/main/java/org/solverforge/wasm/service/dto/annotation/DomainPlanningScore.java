package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningScore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainPlanningScore implements PlanningAnnotation {
    @JsonProperty("bendable_hard_levels")
    private Integer bendableHardLevelsSize;

    @JsonProperty("bendable_soft_levels")
    private Integer bendableSoftLevelsSize;

    public DomainPlanningScore() {
    }

    public DomainPlanningScore(Integer bendableHardLevelsSize, Integer bendableSoftLevelsSize) {
        this.bendableHardLevelsSize = bendableHardLevelsSize;
        this.bendableSoftLevelsSize = bendableSoftLevelsSize;
    }

    public Integer getBendableHardLevelsSize() {
        return bendableHardLevelsSize;
    }

    public Integer getBendableSoftLevelsSize() {
        return bendableSoftLevelsSize;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningScore.class;
    }

    @Override
    public boolean definesPlanningSolution() {
        return true;
    }

    @Override
    @JsonIgnore
    public List<AnnotationElement> getAnnotationElements() {
        if (bendableHardLevelsSize == null && bendableSoftLevelsSize == null) {
            return Collections.emptyList();
        }
        List<AnnotationElement> elements = new ArrayList<>();
        if (bendableHardLevelsSize != null) {
            elements.add(AnnotationElement.of("bendableHardLevelsSize",
                    AnnotationValue.ofInt(bendableHardLevelsSize)));
        }
        if (bendableSoftLevelsSize != null) {
            elements.add(AnnotationElement.of("bendableSoftLevelsSize",
                    AnnotationValue.ofInt(bendableSoftLevelsSize)));
        }
        return elements;
    }
}
