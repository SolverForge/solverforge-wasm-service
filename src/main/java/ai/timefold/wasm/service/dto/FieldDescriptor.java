package ai.timefold.wasm.service.dto;

import java.util.List;

import ai.timefold.wasm.service.dto.annotation.PlanningAnnotation;

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
            @JsonProperty("accessor") @Nullable DomainAccessor accessor,
            @JsonProperty("annotations") @Nullable List<PlanningAnnotation> annotations) {
        this.type = type;
        this.annotations = annotations;
        this.accessor = accessor;
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
