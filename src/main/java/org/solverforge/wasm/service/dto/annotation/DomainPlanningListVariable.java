package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainPlanningListVariable implements PlanningVariableAnnotation {
    private final boolean allowsUnassignedValues;
    private final String[] valueRangeProviderRefs;

    @JsonCreator
    public DomainPlanningListVariable(
            @JsonProperty("allowsUnassignedValues") Boolean allowsUnassignedValues,
            @JsonProperty("valueRangeProviderRefs") String[] valueRangeProviderRefs) {
        this.allowsUnassignedValues = allowsUnassignedValues != null ? allowsUnassignedValues : false;
        this.valueRangeProviderRefs = valueRangeProviderRefs != null ? valueRangeProviderRefs : new String[0];
    }

    public DomainPlanningListVariable() {
        this(false, new String[0]);
    }

    public boolean isAllowsUnassignedValues() {
        return allowsUnassignedValues;
    }

    public String[] getValueRangeProviderRefs() {
        return valueRangeProviderRefs;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningListVariable.class;
    }

    @Override
    public boolean definesPlanningEntity() {
        return true;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        var elements = new ArrayList<AnnotationElement>();
        elements.add(AnnotationElement.of("allowsUnassignedValues", AnnotationValue.of(allowsUnassignedValues)));
        if (valueRangeProviderRefs.length > 0) {
            var refValues = Arrays.stream(valueRangeProviderRefs)
                    .map(AnnotationValue::of)
                    .toList();
            elements.add(AnnotationElement.of("valueRangeProviderRefs",
                    AnnotationValue.ofArray(refValues)));
        }
        return elements;
    }
}
