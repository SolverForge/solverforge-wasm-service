package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainCascadingUpdateShadowVariable implements PlanningAnnotation {
    @JsonProperty("target_method_name")
    String targetMethodName;

    public DomainCascadingUpdateShadowVariable() {
        this.targetMethodName = "";
    }

    @JsonCreator
    public DomainCascadingUpdateShadowVariable(@JsonProperty("target_method_name") String targetMethodName) {
        this.targetMethodName = (targetMethodName != null) ? targetMethodName : "";
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return CascadingUpdateShadowVariable.class;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        return List.of(
                AnnotationElement.of("targetMethodName", AnnotationValue.ofString(targetMethodName))
        );
    }

    /**
     * Shadow variables define a planning entity because they're only valid on entities.
     */
    @Override
    public boolean definesPlanningEntity() {
        return true;
    }

    /**
     * Shadow variables count as "planning variable or shadow variable" for entity validation.
     */
    @Override
    public boolean isShadowVariable() {
        return true;
    }

    public String getTargetMethodName() {
        return targetMethodName;
    }
}
