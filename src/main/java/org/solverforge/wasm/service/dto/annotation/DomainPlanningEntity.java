package org.solverforge.wasm.service.dto.annotation;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class DomainPlanningEntity implements ClassAnnotation {

    @JsonCreator
    public DomainPlanningEntity() {
    }

    @Override
    public boolean definesPlanningEntity() {
        return true;
    }
}
