package ai.timefold.wasm.service.dto.constraint;

import java.util.Collections;
import java.util.List;

import ai.timefold.wasm.service.dto.constraint.joiner.DataJoiner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public record IfNotExistsComponent(@JsonProperty("className") String className, @Nullable @JsonProperty("joiners") List<DataJoiner> joiners) implements StreamComponent {
    @JsonCreator
    public IfNotExistsComponent {
    }

    public IfNotExistsComponent() {
        this(null, null);
    }

    public IfNotExistsComponent(String className) {
        this(className, Collections.emptyList());
    }

    @Override
    public String kind() {
        return "ifNotExists";
    }

    public List<DataJoiner> getJoiners() {
        if (joiners == null) {
            return Collections.emptyList();
        }
        return joiners;
    }
}
