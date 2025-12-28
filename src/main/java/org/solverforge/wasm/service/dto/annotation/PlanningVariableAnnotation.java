package org.solverforge.wasm.service.dto.annotation;

/**
 * Annotations for genuine planning variables that Timefold modifies during solving.
 * Setters must invalidate function cache.
 */
public sealed interface PlanningVariableAnnotation extends PlanningAnnotation
    permits DomainPlanningVariable, DomainPlanningListVariable {}
