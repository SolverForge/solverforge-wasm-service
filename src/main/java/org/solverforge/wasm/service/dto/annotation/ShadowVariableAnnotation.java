package org.solverforge.wasm.service.dto.annotation;

/**
 * Annotations for shadow variables computed from genuine variables.
 * Getters read field directly (managed by update methods).
 */
public sealed interface ShadowVariableAnnotation extends PlanningAnnotation
    permits DomainCascadingUpdateShadowVariable, DomainInverseRelationShadowVariable,
            DomainNextElementShadowVariable, DomainPreviousElementShadowVariable {}
