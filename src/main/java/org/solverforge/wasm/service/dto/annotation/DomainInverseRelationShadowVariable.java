package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainInverseRelationShadowVariable implements PlanningAnnotation {
    @JsonProperty("source_variable_name")
    String sourceVariableName;

    public DomainInverseRelationShadowVariable() {
        this.sourceVariableName = "";
    }

    @JsonCreator
    public DomainInverseRelationShadowVariable(@JsonProperty("source_variable_name") String sourceVariableName) {
        this.sourceVariableName = (sourceVariableName != null) ? sourceVariableName : "";
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return InverseRelationShadowVariable.class;
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
