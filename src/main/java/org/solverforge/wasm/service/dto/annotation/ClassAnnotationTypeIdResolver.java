package org.solverforge.wasm.service.dto.annotation;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class ClassAnnotationTypeIdResolver implements TypeIdResolver {
    private static final Map<String, JavaType> ID_TO_TYPE = Map.of(
            "PlanningEntity", TypeFactory.defaultInstance().constructType(DomainPlanningEntity.class),
            "PlanningSolution", TypeFactory.defaultInstance().constructType(DomainPlanningSolution.class)
    );

    @Override
    public void init(JavaType baseType) {
        // Static initialization
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof DomainPlanningEntity) {
            return "PlanningEntity";
        } else if (value instanceof DomainPlanningSolution) {
            return "PlanningSolution";
        }
        throw new IllegalArgumentException("Unsupported value " + value);
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public String idFromBaseType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        return ID_TO_TYPE.get(id);
    }

    @Override
    public String getDescForKnownTypeIds() {
        return String.join(",", ID_TO_TYPE.keySet());
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
