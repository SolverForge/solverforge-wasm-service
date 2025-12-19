package org.solverforge.wasm.service.dto;

import java.util.LinkedHashMap;
import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.solverforge.wasm.service.dto.annotation.ClassAnnotation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@NullMarked
public class DomainObject {
    String name;
    // LinkedHashMap preserves insertion order - Jackson deserializes to LinkedHashMap by default
    LinkedHashMap<String, FieldDescriptor> fieldDescriptorMap;
    @Nullable
    DomainObjectMapper domainObjectMapper;
    @Nullable
    List<ClassAnnotation> classAnnotations;

    @JsonCreator
    public DomainObject(@JsonProperty("fields") LinkedHashMap<String, FieldDescriptor> fieldDescriptorMap,
            @JsonProperty("mapper") @Nullable DomainObjectMapper domainObjectMapper,
            @JsonProperty("annotations") @Nullable List<ClassAnnotation> classAnnotations) {
        this.name = null;
        this.fieldDescriptorMap = fieldDescriptorMap;
        this.domainObjectMapper = domainObjectMapper;
        this.classAnnotations = classAnnotations;
    }

    void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public LinkedHashMap<String, FieldDescriptor> getFieldDescriptorMap() {
        return fieldDescriptorMap;
    }

    public @Nullable DomainObjectMapper getDomainObjectMapper() {
        return domainObjectMapper;
    }

    public @Nullable List<ClassAnnotation> getClassAnnotations() {
        return classAnnotations;
    }

    /**
     * Check if this class is marked as a planning entity via class-level annotation.
     */
    public boolean isPlanningEntity() {
        if (classAnnotations == null) {
            return false;
        }
        return classAnnotations.stream().anyMatch(ClassAnnotation::definesPlanningEntity);
    }

    /**
     * Check if this class is marked as a planning solution via class-level annotation.
     */
    public boolean isPlanningSolution() {
        if (classAnnotations == null) {
            return false;
        }
        return classAnnotations.stream().anyMatch(ClassAnnotation::definesPlanningSolution);
    }
}
