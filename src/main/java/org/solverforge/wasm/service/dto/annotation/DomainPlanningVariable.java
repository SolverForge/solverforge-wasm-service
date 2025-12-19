package org.solverforge.wasm.service.dto.annotation;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DomainPlanningVariable implements PlanningAnnotation {
    private final boolean allowsUnassigned;
    private final String[] valueRangeProviderRefs;

    @JsonCreator
    public DomainPlanningVariable(
            @JsonProperty("allowsUnassigned") Boolean allowsUnassigned,
            @JsonProperty("valueRangeProviderRefs") String[] valueRangeProviderRefs) {
        this.allowsUnassigned = allowsUnassigned != null ? allowsUnassigned : false;
        this.valueRangeProviderRefs = valueRangeProviderRefs != null ? valueRangeProviderRefs : new String[0];
    }

    public DomainPlanningVariable() {
        this(false, new String[0]);
    }

    public boolean isAllowsUnassigned() {
        return allowsUnassigned;
    }

    public String[] getValueRangeProviderRefs() {
        return valueRangeProviderRefs;
    }

    @Override
    public Class<? extends Annotation> annotationClass() {
        return PlanningVariable.class;
    }

    @Override
    public boolean definesPlanningEntity() {
        return true;
    }

    @Override
    public List<AnnotationElement> getAnnotationElements() {
        var elements = new ArrayList<AnnotationElement>();
        elements.add(AnnotationElement.of("allowsUnassigned", AnnotationValue.of(allowsUnassigned)));
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
