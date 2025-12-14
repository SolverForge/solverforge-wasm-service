package ai.timefold.wasm.service.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;

import ai.timefold.solver.core.api.function.PentaFunction;
import ai.timefold.solver.core.api.function.PentaPredicate;
import ai.timefold.solver.core.api.function.QuadFunction;
import ai.timefold.solver.core.api.function.QuadPredicate;
import ai.timefold.solver.core.api.function.ToIntQuadFunction;
import ai.timefold.solver.core.api.function.ToIntTriFunction;
import ai.timefold.solver.core.api.function.ToLongQuadFunction;
import ai.timefold.solver.core.api.function.ToLongTriFunction;
import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.solver.core.api.function.TriPredicate;
import ai.timefold.wasm.service.ExportCache;
import ai.timefold.wasm.service.FunctionCache;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.classgen.WasmList;
import ai.timefold.wasm.service.classgen.WasmListAccessor;
import ai.timefold.wasm.service.classgen.WasmObject;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.dylibso.chicory.runtime.ExportFunction;
import com.dylibso.chicory.runtime.Instance;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@NullMarked
public class WasmFunction {
    @JsonValue
    final String wasmFunctionName;

    @Nullable
    String relationFunctionName;
    @Nullable
    String hashFunctionName;
    @Nullable
    String comparatorFunctionName;

    @JsonCreator
    public WasmFunction(String functionName) {
        this.wasmFunctionName = functionName;
    }

    private ExportFunction getExport(String name, Instance instance) {
        ExportCache cache = SolverResource.EXPORT_CACHE.get();
        if (cache != null) {
            return cache.get(name);
        }
        return instance.export(name);
    }

    // ========== PREDICATES (Boolean) ==========

    public Object asPredicate(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var fn = wasmFunctionName;
        return switch (tupleSize) {
            case 1 -> (Predicate<WasmObject>) a -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBool1(fn, p1);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1)[0] != 0;
                if (cache != null) cache.putBool1(fn, p1, result);
                return result;
            };
            case 2 -> (BiPredicate<WasmObject, WasmObject>) (a, b) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBool2(fn, p1, p2);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2)[0] != 0;
                if (cache != null) cache.putBool2(fn, p1, p2, result);
                return result;
            };
            case 3 -> (TriPredicate<WasmObject, WasmObject, WasmObject>) (a, b, c) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBool3(fn, p1, p2, p3);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2, p3)[0] != 0;
                if (cache != null) cache.putBool3(fn, p1, p2, p3, result);
                return result;
            };
            case 4 -> (QuadPredicate<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer(), p4 = d.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBool4(fn, p1, p2, p3, p4);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2, p3, p4)[0] != 0;
                if (cache != null) cache.putBool4(fn, p1, p2, p3, p4, result);
                return result;
            };
            case 5 -> (PentaPredicate<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer(), p4 = d.getMemoryPointer(), p5 = e.getMemoryPointer();
                if (cache != null) {
                    Boolean cached = cache.getBool5(fn, p1, p2, p3, p4, p5);
                    if (cached != null) return cached;
                }
                boolean result = wasmFunction.apply(p1, p2, p3, p4, p5)[0] != 0;
                if (cache != null) cache.putBool5(fn, p1, p2, p3, p4, p5, result);
                return result;
            };
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // ========== MAPPERS (return WasmObject pointer) ==========

    public Object asFunction(int tupleSize, Instance instance) {
        if (comparatorFunctionName == null) {
            if (relationFunctionName == null) {
                return asFunctionWithDefaultEqualsAndComparator(tupleSize, instance);
            } else {
                return asFunctionWithCustomEquals(tupleSize, instance);
            }
        } else {
            return asFunctionWithCustomComparator(tupleSize, instance);
        }
    }

    // NO CACHING - mappers read mutable fields (planning variables)
    private Object asFunctionWithCustomComparator(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var comparator = getComparator(instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0], comparator);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer())[0], comparator);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer())[0], comparator);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer())[0], comparator);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer(), e.getMemoryPointer())[0], comparator);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // NO CACHING - mappers read mutable fields (planning variables)
    private Object asFunctionWithCustomEquals(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var relation = getRelation(instance);
        var hasher = getHasher(instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0], relation, hasher);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer())[0], relation, hasher);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer())[0], relation, hasher);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer())[0], relation, hasher);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer(), e.getMemoryPointer())[0], relation, hasher);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // NO CACHING - mappers read mutable fields (planning variables)
    private Object asFunctionWithDefaultEqualsAndComparator(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmObject>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0]);
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmObject>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer())[0]);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer())[0]);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer())[0]);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer(), e.getMemoryPointer())[0]);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // NO CACHING - these read mutable fields via navigation (e.g., shift.employee.dates)
    public Object asToListFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, WasmList<WasmObject>>) a -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer())[0]).asList();
            case 2 -> (BiFunction<WasmObject, WasmObject, WasmList<WasmObject>>) (a, b) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer())[0]).asList();
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer())[0]).asList();
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c, d) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer())[0]).asList();
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, WasmList<WasmObject>>) (a, b, c, d, e) -> WasmObject.ofExisting(instance, (int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer(), e.getMemoryPointer())[0]).asList();
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // NO CACHING - these read mutable fields via navigation
    public Object asToIntListFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        return switch (tupleSize) {
            case 1 -> (Function<WasmObject, List<WasmObject>>) a -> readIntListWrapped((int) wasmFunction.apply(a.getMemoryPointer())[0]);
            case 2 -> (BiFunction<WasmObject, WasmObject, List<WasmObject>>) (a, b) -> readIntListWrapped((int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer())[0]);
            case 3 -> (TriFunction<WasmObject, WasmObject, WasmObject, List<WasmObject>>) (a, b, c) -> readIntListWrapped((int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer())[0]);
            case 4 -> (QuadFunction<WasmObject, WasmObject, WasmObject, WasmObject, List<WasmObject>>) (a, b, c, d) -> readIntListWrapped((int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer())[0]);
            case 5 -> (PentaFunction<WasmObject, WasmObject, WasmObject, WasmObject, WasmObject, List<WasmObject>>) (a, b, c, d, e) -> readIntListWrapped((int) wasmFunction.apply(a.getMemoryPointer(), b.getMemoryPointer(), c.getMemoryPointer(), d.getMemoryPointer(), e.getMemoryPointer())[0]);
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    private static List<WasmObject> readIntListWrapped(int listPtr) {
        if (listPtr == 0) {
            return List.of();
        }
        var listAccessor = SolverResource.LIST_ACCESSOR.get();
        var listObj = WasmObject.ofExisting(listAccessor.getWasmInstance(), listPtr);
        int size = listAccessor.getLength(listObj);
        List<WasmObject> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            var item = listAccessor.getItem(listObj, i, WasmObject::wrappingInt);
            result.add(item);
        }
        return result;
    }

    // ========== TO INT (weighers) ==========

    public Object asToIntFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var fn = wasmFunctionName;
        return switch (tupleSize) {
            case 1 -> (ToIntFunction<WasmObject>) a -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer();
                if (cache != null) {
                    Integer cached = cache.getInt1(fn, p1);
                    if (cached != null) return cached;
                }
                int result = (int) wasmFunction.apply(p1)[0];
                if (cache != null) cache.putInt1(fn, p1, result);
                return result;
            };
            case 2 -> (ToIntBiFunction<WasmObject, WasmObject>) (a, b) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer();
                if (cache != null) {
                    Integer cached = cache.getInt2(fn, p1, p2);
                    if (cached != null) return cached;
                }
                int result = (int) wasmFunction.apply(p1, p2)[0];
                if (cache != null) cache.putInt2(fn, p1, p2, result);
                return result;
            };
            case 3 -> (ToIntTriFunction<WasmObject, WasmObject, WasmObject>) (a, b, c) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer();
                if (cache != null) {
                    Integer cached = cache.getInt3(fn, p1, p2, p3);
                    if (cached != null) return cached;
                }
                int result = (int) wasmFunction.apply(p1, p2, p3)[0];
                if (cache != null) cache.putInt3(fn, p1, p2, p3, result);
                return result;
            };
            case 4 -> (ToIntQuadFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer(), p4 = d.getMemoryPointer();
                if (cache != null) {
                    Integer cached = cache.getInt4(fn, p1, p2, p3, p4);
                    if (cached != null) return cached;
                }
                int result = (int) wasmFunction.apply(p1, p2, p3, p4)[0];
                if (cache != null) cache.putInt4(fn, p1, p2, p3, p4, result);
                return result;
            };
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // ========== TO LONG ==========

    public Object asToLongFunction(int tupleSize, Instance instance) {
        var wasmFunction = getExport(wasmFunctionName, instance);
        var fn = wasmFunctionName;
        return switch (tupleSize) {
            case 1 -> (ToLongFunction<WasmObject>) a -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer();
                if (cache != null) {
                    Long cached = cache.getLong1(fn, p1);
                    if (cached != null) return cached;
                }
                long result = wasmFunction.apply(p1)[0];
                if (cache != null) cache.putLong1(fn, p1, result);
                return result;
            };
            case 2 -> (ToLongBiFunction<WasmObject, WasmObject>) (a, b) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer();
                if (cache != null) {
                    Long cached = cache.getLong2(fn, p1, p2);
                    if (cached != null) return cached;
                }
                long result = wasmFunction.apply(p1, p2)[0];
                if (cache != null) cache.putLong2(fn, p1, p2, result);
                return result;
            };
            case 3 -> (ToLongTriFunction<WasmObject, WasmObject, WasmObject>) (a, b, c) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer();
                if (cache != null) {
                    Long cached = cache.getLong3(fn, p1, p2, p3);
                    if (cached != null) return cached;
                }
                long result = wasmFunction.apply(p1, p2, p3)[0];
                if (cache != null) cache.putLong3(fn, p1, p2, p3, result);
                return result;
            };
            case 4 -> (ToLongQuadFunction<WasmObject, WasmObject, WasmObject, WasmObject>) (a, b, c, d) -> {
                var cache = SolverResource.FUNCTION_CACHE.get();
                int p1 = a.getMemoryPointer(), p2 = b.getMemoryPointer(), p3 = c.getMemoryPointer(), p4 = d.getMemoryPointer();
                if (cache != null) {
                    Long cached = cache.getLong4(fn, p1, p2, p3, p4);
                    if (cached != null) return cached;
                }
                long result = wasmFunction.apply(p1, p2, p3, p4)[0];
                if (cache != null) cache.putLong4(fn, p1, p2, p3, p4, result);
                return result;
            };
            default -> throw new IllegalArgumentException("Unexpected value: " + tupleSize);
        };
    }

    // ========== COMPARATOR/RELATION/HASHER (used for custom equals) ==========

    private Comparator<Integer> getComparator(Instance instance) {
        var wasmComparator = getExport(comparatorFunctionName, instance);
        var fn = comparatorFunctionName;
        return (a, b) -> {
            var cache = SolverResource.FUNCTION_CACHE.get();
            if (cache != null) {
                Integer cached = cache.getInt2(fn, a, b);
                if (cached != null) return cached;
            }
            int result = (int) wasmComparator.apply(a, b)[0];
            if (cache != null) cache.putInt2(fn, a, b, result);
            return result;
        };
    }

    private BiPredicate<Integer, Integer> getRelation(Instance instance) {
        var wasmRelation = getExport(relationFunctionName, instance);
        var fn = relationFunctionName;
        return (a, b) -> {
            var cache = SolverResource.FUNCTION_CACHE.get();
            if (cache != null) {
                Boolean cached = cache.getBool2(fn, a, b);
                if (cached != null) return cached;
            }
            boolean result = wasmRelation.apply(a, b)[0] != 0;
            if (cache != null) cache.putBool2(fn, a, b, result);
            return result;
        };
    }

    private ToIntFunction<Integer> getHasher(Instance instance) {
        var wasmHasher = getExport(hashFunctionName, instance);
        var fn = hashFunctionName;
        return a -> {
            var cache = SolverResource.FUNCTION_CACHE.get();
            if (cache != null) {
                Integer cached = cache.getInt1(fn, a);
                if (cached != null) return cached;
            }
            int result = (int) wasmHasher.apply(a)[0];
            if (cache != null) cache.putInt1(fn, a, result);
            return result;
        };
    }

    public String getWasmFunctionName() {
        return wasmFunctionName;
    }

    public void setRelationFunctionName(@Nullable String relationFunctionName) {
        this.relationFunctionName = relationFunctionName;
    }

    public void setHashFunctionName(@Nullable String hashFunctionName) {
        this.hashFunctionName = hashFunctionName;
    }

    public void setComparatorFunctionName(String comparatorFunctionName) {
        this.comparatorFunctionName = comparatorFunctionName;
    }

    @Override
    public String toString() {
        return wasmFunctionName;
    }
}
