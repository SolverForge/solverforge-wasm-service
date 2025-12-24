package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.NextElementShadowVariable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainNextElementShadowVariable implements PlanningAnnotation {
    @JsonProperty("source_variable_name")
    String sourceVariableName;

    public DomainNextElementShadowVariable() {
        this.sourceVariableName = "";
    }

    @JsonCreator
    public DomainNextElementShadowVariable(@JsonProperty("source_variable_name") String sourceVariableName) {
        this.sourceVariableName = (sourceVariableName != null) ? sourceVariableName : "";
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return NextElementShadowVariable.class;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        return List.of(
                AnnotationElement.of("sourceVariableName", AnnotationValue.ofString(sourceVariableName))
        );
    }

    /**
     * Shadow variables count as "planning variable or shadow variable" for entity validation.
     */
    public boolean isShadowVariable() {
        return true;
    }
}
