package org.solverforge.wasm.service.classgen;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import org.solverforge.wasm.service.FunctionCache;
import org.solverforge.wasm.service.SolverResource;

import org.apache.commons.collections4.map.ConcurrentReferenceHashMap;

import com.dylibso.chicory.runtime.Instance;

public class WasmObject implements Comparable<WasmObject> {
    /**
     * Global cache of entity objects by (Instance, memoryPointer).
     * This ensures the same Java object is returned for the same WASM memory location,
     * which is critical for Timefold's shadow variable tracking (uses object identity).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final ConcurrentReferenceHashMap<Instance, Map<Integer, WasmObject>> entityCache =
            (ConcurrentReferenceHashMap) new ConcurrentReferenceHashMap.Builder<>()
                    .weakKeys().strongValues().get();

    public final Instance wasmInstance;
    public final int memoryPointer;
    private final Comparator<Integer> comparator;
    private final ToIntFunction<Integer> hasher;
    private final BiPredicate<Integer, Integer> equalRelation;

    /**
     * Called when a planning variable on this entity is modified.
     * Invalidates any cached function results involving this entity.
     */
    public void invalidateFunctionCache() {
        FunctionCache cache = SolverResource.FUNCTION_CACHE.get();
        if (cache != null) {
            cache.invalidateEntity(memoryPointer);
        }
    }

    public static final Function<Integer, WasmObject> WRAPPING_INT = WasmObject::wrappingInt;
    public static final Function<Double, WasmObject> WRAPPING_DOUBLE = WasmObject::wrappingDouble;
    public static final Function<WasmObject, WasmList<WasmObject>> TO_LIST = WasmObject::asList;
    public static final Function<Object, WasmObject> CONSTANT_NULL = _ -> new WasmObject(SolverResource.INSTANCE.get(), 0);

    private static final BiPredicate<Integer, Integer> DEFAULT_EQUALS = Integer::equals;
    private static final ToIntFunction<Integer> DEFAULT_HASH = Object::hashCode;
    private static final Comparator<Integer> DEFAULT_COMPARATOR = Comparator.comparingInt(memoryAddress -> memoryAddress);

    public WasmObject() {
        // Required for cloning
        memoryPointer = 0;
        wasmInstance = null;
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Allocator allocator, Instance wasmInstance, int size) {
        this.wasmInstance = wasmInstance;
        memoryPointer = allocator.allocate(size);
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Instance wasmInstance, int memoryPointer) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        equalRelation = DEFAULT_EQUALS;
        hasher = DEFAULT_HASH;
        comparator = DEFAULT_COMPARATOR;
    }

    public WasmObject(Instance wasmInstance, int memoryPointer,
            BiPredicate<Integer, Integer> equalRelation,
            ToIntFunction<Integer> hasher,
            Comparator<Integer> comparator) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        this.equalRelation = Objects.requireNonNullElse(equalRelation, DEFAULT_EQUALS);
        this.hasher = Objects.requireNonNullElse(hasher, DEFAULT_HASH);
        this.comparator = Objects.requireNonNullElse(comparator, DEFAULT_COMPARATOR);
    }

    public WasmObject(Instance wasmInstance, int memoryPointer,
            Comparator<Integer> comparator) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
        this.equalRelation = (a, b) -> a.compareTo(b) == 0;
        this.hasher = _ -> 0;
        this.comparator = comparator;
    }

    public int getMemoryPointer() {
        return memoryPointer;
    }

    protected int readIntField(int fieldOffset) {
        return wasmInstance.memory().readInt(memoryPointer + fieldOffset);
    }

    protected long readLongField(int fieldOffset) {
        return wasmInstance.memory().readLong(memoryPointer + fieldOffset);
    }

    protected float readFloatField(int fieldOffset) {
        return wasmInstance.memory().readFloat(memoryPointer + fieldOffset);
    }

    protected double readDoubleField(int fieldOffset) {
        return wasmInstance.memory().readDouble(memoryPointer + fieldOffset);
    }

    protected WasmObject readReferenceField(int fieldOffset) {
        var pointer = wasmInstance.memory().readI32(memoryPointer + fieldOffset);
        if (pointer == 0) {
            return null;
        }
        return ofExisting(wasmInstance, (int) pointer);
    }

    public WasmList<WasmObject> asList() {
        return WasmList.ofExisting(memoryPointer, WasmObject.class);
    }

    public static WasmObject wrappingInt(int value) {
        return new WasmObject(null, value);
    }

    public static WasmObject wrappingDouble(double value) {
        return new WasmObject(null, Float.floatToIntBits((float) value));
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer) {
        return ofExistingOrDefault(wasmInstance, memoryPointer,
                new WasmObject(wasmInstance, memoryPointer));
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer, Comparator<Integer> comparator) {
        return ofExistingOrDefault(wasmInstance, memoryPointer,
                new WasmObject(wasmInstance, memoryPointer, comparator));
    }

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer, BiPredicate<Integer, Integer> equalRelation,
            ToIntFunction<Integer> hasher) {
        return ofExistingOrDefault(wasmInstance, memoryPointer,
                new WasmObject(wasmInstance, memoryPointer, equalRelation, hasher, null));
    }

    /**
     * Gets a cached entity for the given memory pointer, or caches and returns the default.
     * This ensures the same Java object is returned for the same WASM memory location.
     */
    public static WasmObject ofExistingOrDefault(Instance wasmInstance,
            int memoryPointer, WasmObject defaultValue) {
        if (memoryPointer == 0) {
            return null;
        }
        var instanceCache = entityCache.computeIfAbsent(wasmInstance, _ -> new ConcurrentHashMap<>());
        return instanceCache.computeIfAbsent(memoryPointer, _ -> defaultValue);
    }

    /**
     * Gets or creates a cached entity for the given memory pointer.
     * This ensures the same Java object is returned for the same WASM memory location,
     * which is critical for Timefold's shadow variable tracking (uses object identity).
     */
    @SuppressWarnings("unchecked")
    public static <Item_ extends WasmObject> Item_ ofExistingOrCreate(Instance wasmInstance,
            int memoryPointer, IntFunction<Item_> factory) {
        if (memoryPointer == 0) {
            return null;
        }
        var instanceCache = entityCache.computeIfAbsent(wasmInstance, _ -> new ConcurrentHashMap<>());
        // Use get + putIfAbsent instead of computeIfAbsent to avoid "recursive update" errors
        // when the factory creates objects that themselves trigger cache lookups
        var existing = instanceCache.get(memoryPointer);
        if (existing != null) {
            return (Item_) existing;
        }
        var created = factory.apply(memoryPointer);
        var prev = instanceCache.putIfAbsent(memoryPointer, created);
        return (Item_) (prev != null ? prev : created);
    }

    @Override
    public int compareTo(WasmObject o) {
        return comparator.compare(memoryPointer, o.memoryPointer);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof WasmObject that)) {
            return false;
        }
        return equalRelation.test(memoryPointer, that.memoryPointer);
    }

    @Override
    public int hashCode() {
        return hasher.applyAsInt(memoryPointer);
    }

    @Override
    public String toString() {
        return "%s(pointer=%x)".formatted(getClass().getSimpleName(), memoryPointer);
    }
}
