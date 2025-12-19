package org.solverforge.wasm.service.dto.annotation;

import com.fasterxml.jackson.annotation.JsonCreator;

public final class DomainPlanningSolution implements ClassAnnotation {

    @JsonCreator
    public DomainPlanningSolution() {
    }

    @Override
    public boolean definesPlanningSolution() {
        return true;
    }
}
