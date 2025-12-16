package org.solverforge.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;

import org.solverforge.wasm.service.dto.constraint.joiner.DataJoiner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record IfExistsComponent(@JsonProperty("className") String className, @Nullable @JsonProperty("joiners") List<DataJoiner> joiners) implements StreamComponent {
    @JsonCreator
    public IfExistsComponent {
    }

    public IfExistsComponent() {
        this(null, null);
    }

    public IfExistsComponent(String className) {
        this(className, Collections.emptyList());
    }

    @Override
    public String kind() {
        return "ifExists";
    }

    public List<DataJoiner> getJoiners() {
        if (joiners == null) {
            return Collections.emptyList();
        }
        return joiners;
    }
}
