package ai.timefold.wasm.service.dto.constraint;

import ai.timefold.wasm.service.dto.WasmFunction;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public record PenalizeComponent(@JsonProperty("weight") String weight, @Nullable @JsonProperty("scaleBy") WasmFunction scaleBy) implements StreamComponent {
    @JsonCreator
    public PenalizeComponent {
    }

    public PenalizeComponent() {
        this("", null);
    }

    @Override
    public String kind() {
        return "penalize";
    }
}
