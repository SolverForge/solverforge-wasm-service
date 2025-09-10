package ai.timefold.wasm.service.dto;

import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public class DomainObject {
    String name;
    Map<String, FieldDescriptor> fieldDescriptorMap;
    @Nullable
    DomainObjectMapper domainObjectMapper;

    @JsonCreator
    public DomainObject(@JsonProperty("fields") Map<String, FieldDescriptor> fieldDescriptorMap,
            @JsonProperty("mapper") @Nullable DomainObjectMapper domainObjectMapper) {
        this.name = null;
        this.fieldDescriptorMap = fieldDescriptorMap;
        this.domainObjectMapper = domainObjectMapper;
    }

    void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, FieldDescriptor> getFieldDescriptorMap() {
        return fieldDescriptorMap;
    }

    public @Nullable DomainObjectMapper getDomainObjectMapper() {
        return domainObjectMapper;
    }
}
