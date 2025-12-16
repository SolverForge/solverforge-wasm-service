package org.solverforge.wasm.service.dto;

import java.util.List;

import org.solverforge.wasm.service.dto.annotation.DomainPlanningScore;
import org.solverforge.wasm.service.dto.annotation.PlanningAnnotation;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public class FieldDescriptor {
    String type;
    @Nullable
    DomainAccessor accessor;
    @Nullable
    List<PlanningAnnotation> annotations;

    public FieldDescriptor(String type,
            List<PlanningAnnotation> annotations) {
        this(type, null, annotations);
    }

    @JsonCreator
    public FieldDescriptor(@JsonProperty("type") String type,
            @JsonProperty("accessor") DomainAccessor accessor,
            @JsonProperty("annotations") @Nullable List<PlanningAnnotation> annotations) {
        this.type = type;
        this.annotations = annotations;
        this.accessor = accessor;
        if (accessor == null && annotations != null && !annotations.stream().anyMatch(annotation -> annotation instanceof DomainPlanningScore)) {
            throw new IllegalArgumentException("accessor must be specified for any non-PlanningScore planning attribute");
        }
    }

    public String getType() {
        return type;
    }

    public @Nullable List<PlanningAnnotation> getAnnotations() {
        return annotations;
    }

    public @Nullable DomainAccessor getAccessor() {
        return accessor;
    }
}
