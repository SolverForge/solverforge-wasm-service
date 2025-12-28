package org.solverforge.wasm.service.dto.annotation;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class AnnotationTypeIdResolver implements TypeIdResolver {
    Map<String, JavaType> idToType;

    @Override
    public void init(JavaType baseType) {
        record Pair(String id, JavaType type) {}
        idToType = getAllConcretePermittedClasses(PlanningAnnotation.class)
                .map(c -> {
                    try {
                        var instance = (PlanningAnnotation) c.getConstructor().newInstance();
                        return new Pair(instance.annotation(), TypeFactory.defaultInstance().constructType(c));
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                            NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toMap(Pair::id, Pair::type));
    }

    /**
     * Recursively get all concrete (non-interface) permitted subclasses of a sealed type.
     */
    private static java.util.stream.Stream<Class<?>> getAllConcretePermittedClasses(Class<?> sealedType) {
        if (sealedType.getPermittedSubclasses() == null) {
            return java.util.stream.Stream.empty();
        }
        return Arrays.stream(sealedType.getPermittedSubclasses())
                .flatMap(c -> {
                    if (c.isInterface() || c.isSealed()) {
                        // Recurse into sub-interfaces or sealed classes
                        return getAllConcretePermittedClasses(c);
                    } else {
                        // Concrete class
                        return java.util.stream.Stream.of(c);
                    }
                });
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof PlanningAnnotation annotation) {
            return annotation.annotation();
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
        return idToType.get(id);
    }

    @Override
    public String getDescForKnownTypeIds() {
        return String.join(",", idToType.keySet());
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
