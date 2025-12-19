package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.Collections;
import java.util.List;

import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainValueRangeProvider implements PlanningAnnotation {
    private final String id;

    @JsonCreator
    public DomainValueRangeProvider(@JsonProperty("id") String id) {
        this.id = id != null ? id : "";
    }

    public DomainValueRangeProvider() {
        this("");
    }

    public String getId() {
        return id;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return ValueRangeProvider.class;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        if (id != null && !id.isEmpty()) {
            return List.of(AnnotationElement.of("id", AnnotationValue.of(id)));
        }
        return Collections.emptyList();
    }
}
