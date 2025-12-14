package ai.timefold.wasm.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ai.timefold.wasm.service.classgen.WasmObject;

/**
 * Caches predicate evaluation results to avoid redundant WASM calls.
 *
 * When a constraint filter like sameEmployee(shift1, shift2) is evaluated,
 * the result only changes if the relevant fields of shift1 or shift2 change.
 * By caching results keyed by memory pointers, we can skip WASM calls when
 * the same pair is evaluated again without field changes.
 *
 * The cache is invalidated when entities are modified during move application.
 * Uses reverse index for O(1) invalidation instead of O(cache_size).
 */
public class PredicateCache {

    /**
     * Cache key for unary predicates (single entity).
     */
    private record UnaryKey(String functionName, int pointer) {}

    /**
     * Cache key for binary predicates (two entities).
     */
    private record BinaryKey(String functionName, int pointer1, int pointer2) {}

    /**
     * Cache key for ternary predicates.
     */
    private record TernaryKey(String functionName, int p1, int p2, int p3) {}

    private final Map<UnaryKey, Boolean> unaryCache = new ConcurrentHashMap<>();
    private final Map<BinaryKey, Boolean> binaryCache = new ConcurrentHashMap<>();
    private final Map<TernaryKey, Boolean> ternaryCache = new ConcurrentHashMap<>();

    // Reverse indices for O(1) invalidation: pointer -> set of keys involving that pointer
    private final Map<Integer, Set<UnaryKey>> unaryByPointer = new ConcurrentHashMap<>();
    private final Map<Integer, Set<BinaryKey>> binaryByPointer = new ConcurrentHashMap<>();
    private final Map<Integer, Set<TernaryKey>> ternaryByPointer = new ConcurrentHashMap<>();

    // Version counter - incremented on each invalidation
    private volatile long version = 0;

    /**
     * Get cached result for a unary predicate, or null if not cached.
     */
    public Boolean getUnary(String functionName, int pointer) {
        return unaryCache.get(new UnaryKey(functionName, pointer));
    }

    /**
     * Cache result for a unary predicate.
     */
    public void putUnary(String functionName, int pointer, boolean result) {
        var key = new UnaryKey(functionName, pointer);
        unaryCache.put(key, result);
        unaryByPointer.computeIfAbsent(pointer, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    /**
     * Get cached result for a binary predicate, or null if not cached.
     */
    public Boolean getBinary(String functionName, int pointer1, int pointer2) {
        return binaryCache.get(new BinaryKey(functionName, pointer1, pointer2));
    }

    /**
     * Cache result for a binary predicate.
     */
    public void putBinary(String functionName, int pointer1, int pointer2, boolean result) {
        var key = new BinaryKey(functionName, pointer1, pointer2);
        binaryCache.put(key, result);
        binaryByPointer.computeIfAbsent(pointer1, k -> ConcurrentHashMap.newKeySet()).add(key);
        if (pointer1 != pointer2) {
            binaryByPointer.computeIfAbsent(pointer2, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /**
     * Get cached result for a ternary predicate.
     */
    public Boolean getTernary(String functionName, int p1, int p2, int p3) {
        return ternaryCache.get(new TernaryKey(functionName, p1, p2, p3));
    }

    /**
     * Cache result for a ternary predicate.
     */
    public void putTernary(String functionName, int p1, int p2, int p3, boolean result) {
        var key = new TernaryKey(functionName, p1, p2, p3);
        ternaryCache.put(key, result);
        ternaryByPointer.computeIfAbsent(p1, k -> ConcurrentHashMap.newKeySet()).add(key);
        if (p2 != p1) {
            ternaryByPointer.computeIfAbsent(p2, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
        if (p3 != p1 && p3 != p2) {
            ternaryByPointer.computeIfAbsent(p3, k -> ConcurrentHashMap.newKeySet()).add(key);
        }
    }

    /**
     * Invalidate cache entries involving the given entity pointer.
     * Called when an entity's planning variable is changed.
     * Uses reverse index for O(1) lookup instead of O(cache_size) iteration.
     */
    public void invalidateEntity(int pointer) {
        // Remove unary entries via reverse index
        var unaryKeys = unaryByPointer.remove(pointer);
        if (unaryKeys != null) {
            for (var key : unaryKeys) {
                unaryCache.remove(key);
            }
        }

        // Remove binary entries via reverse index
        var binaryKeys = binaryByPointer.remove(pointer);
        if (binaryKeys != null) {
            for (var key : binaryKeys) {
                binaryCache.remove(key);
                // Also remove from the other pointer's reverse index
                if (key.pointer1 != pointer) {
                    var otherSet = binaryByPointer.get(key.pointer1);
                    if (otherSet != null) otherSet.remove(key);
                }
                if (key.pointer2 != pointer) {
                    var otherSet = binaryByPointer.get(key.pointer2);
                    if (otherSet != null) otherSet.remove(key);
                }
            }
        }

        // Remove ternary entries via reverse index
        var ternaryKeys = ternaryByPointer.remove(pointer);
        if (ternaryKeys != null) {
            for (var key : ternaryKeys) {
                ternaryCache.remove(key);
                // Also remove from other pointers' reverse indices
                if (key.p1 != pointer) {
                    var otherSet = ternaryByPointer.get(key.p1);
                    if (otherSet != null) otherSet.remove(key);
                }
                if (key.p2 != pointer) {
                    var otherSet = ternaryByPointer.get(key.p2);
                    if (otherSet != null) otherSet.remove(key);
                }
                if (key.p3 != pointer) {
                    var otherSet = ternaryByPointer.get(key.p3);
                    if (otherSet != null) otherSet.remove(key);
                }
            }
        }

        version++;
    }

    /**
     * Clear all cached results. Called at the start of solving or on major changes.
     */
    public void clear() {
        unaryCache.clear();
        binaryCache.clear();
        ternaryCache.clear();
        unaryByPointer.clear();
        binaryByPointer.clear();
        ternaryByPointer.clear();
        version++;
    }

    /**
     * Get statistics about cache usage.
     */
    public String getStats() {
        return String.format("PredicateCache[unary=%d, binary=%d, ternary=%d, version=%d]",
                unaryCache.size(), binaryCache.size(), ternaryCache.size(), version);
    }

    public long getVersion() {
        return version;
    }
}
