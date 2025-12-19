package org.solverforge.wasm.service.dto.annotation;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Class-level annotations (applied to the class itself, not fields).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "annotation", visible = true)
@JsonTypeIdResolver(ClassAnnotationTypeIdResolver.class)
public sealed interface ClassAnnotation
        permits DomainPlanningEntity, DomainPlanningSolution {

    /**
     * Whether this annotation marks a class as a planning entity.
     */
    default boolean definesPlanningEntity() {
        return false;
    }

    /**
     * Whether this annotation marks a class as a planning solution.
     */
    default boolean definesPlanningSolution() {
        return false;
    }
}
