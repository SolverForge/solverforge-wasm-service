package ai.timefold.wasm.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches ALL WASM function call results to avoid redundant calls.
 *
 * Supports caching for:
 * - Boolean results (predicates/filters)
 * - Integer results (mappers returning pointers, ToInt weighers)
 * - Long results (ToLong functions)
 *
 * Uses reverse indices for O(1) invalidation when entities change.
 */
public class FunctionCache {

    private record UnaryKey(String functionName, int p1) {}
    private record BinaryKey(String functionName, int p1, int p2) {}
    private record TernaryKey(String functionName, int p1, int p2, int p3) {}
    private record QuadKey(String functionName, int p1, int p2, int p3, int p4) {}
    private record PentaKey(String functionName, int p1, int p2, int p3, int p4, int p5) {}

    // Boolean caches (predicates)
    private final Map<UnaryKey, Boolean> boolUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, Boolean> boolBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, Boolean> boolTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, Boolean> boolQuad = new ConcurrentHashMap<>();
    private final Map<PentaKey, Boolean> boolPenta = new ConcurrentHashMap<>();

    // Integer caches (mappers, ToInt functions)
    private final Map<UnaryKey, Integer> intUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, Integer> intBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, Integer> intTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, Integer> intQuad = new ConcurrentHashMap<>();
    private final Map<PentaKey, Integer> intPenta = new ConcurrentHashMap<>();

    // Long caches (ToLong functions)
    private final Map<UnaryKey, Long> longUnary = new ConcurrentHashMap<>();
    private final Map<BinaryKey, Long> longBinary = new ConcurrentHashMap<>();
    private final Map<TernaryKey, Long> longTernary = new ConcurrentHashMap<>();
    private final Map<QuadKey, Long> longQuad = new ConcurrentHashMap<>();

    // Reverse indices for O(1) invalidation
    private final Map<Integer, Set<Object>> keysByPointer = new ConcurrentHashMap<>();

    private volatile long version = 0;

    // ========== Boolean (Predicate) Methods ==========

    public Boolean getBool1(String fn, int p1) {
        return boolUnary.get(new UnaryKey(fn, p1));
    }

    public void putBool1(String fn, int p1, boolean result) {
        var key = new UnaryKey(fn, p1);
        boolUnary.put(key, result);
        addToReverseIndex(p1, key);
    }

    public Boolean getBool2(String fn, int p1, int p2) {
        return boolBinary.get(new BinaryKey(fn, p1, p2));
    }

    public void putBool2(String fn, int p1, int p2, boolean result) {
        var key = new BinaryKey(fn, p1, p2);
        boolBinary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
    }

    public Boolean getBool3(String fn, int p1, int p2, int p3) {
        return boolTernary.get(new TernaryKey(fn, p1, p2, p3));
    }

    public void putBool3(String fn, int p1, int p2, int p3, boolean result) {
        var key = new TernaryKey(fn, p1, p2, p3);
        boolTernary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
    }

    public Boolean getBool4(String fn, int p1, int p2, int p3, int p4) {
        return boolQuad.get(new QuadKey(fn, p1, p2, p3, p4));
    }

    public void putBool4(String fn, int p1, int p2, int p3, int p4, boolean result) {
        var key = new QuadKey(fn, p1, p2, p3, p4);
        boolQuad.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
        if (p4 != p1 && p4 != p2 && p4 != p3) addToReverseIndex(p4, key);
    }

    public Boolean getBool5(String fn, int p1, int p2, int p3, int p4, int p5) {
        return boolPenta.get(new PentaKey(fn, p1, p2, p3, p4, p5));
    }

    public void putBool5(String fn, int p1, int p2, int p3, int p4, int p5, boolean result) {
        var key = new PentaKey(fn, p1, p2, p3, p4, p5);
        boolPenta.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
        if (p4 != p1 && p4 != p2 && p4 != p3) addToReverseIndex(p4, key);
        if (p5 != p1 && p5 != p2 && p5 != p3 && p5 != p4) addToReverseIndex(p5, key);
    }

    // ========== Integer (Mapper/ToInt) Methods ==========

    public Integer getInt1(String fn, int p1) {
        return intUnary.get(new UnaryKey(fn, p1));
    }

    public void putInt1(String fn, int p1, int result) {
        var key = new UnaryKey(fn, p1);
        intUnary.put(key, result);
        addToReverseIndex(p1, key);
    }

    public Integer getInt2(String fn, int p1, int p2) {
        return intBinary.get(new BinaryKey(fn, p1, p2));
    }

    public void putInt2(String fn, int p1, int p2, int result) {
        var key = new BinaryKey(fn, p1, p2);
        intBinary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
    }

    public Integer getInt3(String fn, int p1, int p2, int p3) {
        return intTernary.get(new TernaryKey(fn, p1, p2, p3));
    }

    public void putInt3(String fn, int p1, int p2, int p3, int result) {
        var key = new TernaryKey(fn, p1, p2, p3);
        intTernary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
    }

    public Integer getInt4(String fn, int p1, int p2, int p3, int p4) {
        return intQuad.get(new QuadKey(fn, p1, p2, p3, p4));
    }

    public void putInt4(String fn, int p1, int p2, int p3, int p4, int result) {
        var key = new QuadKey(fn, p1, p2, p3, p4);
        intQuad.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
        if (p4 != p1 && p4 != p2 && p4 != p3) addToReverseIndex(p4, key);
    }

    public Integer getInt5(String fn, int p1, int p2, int p3, int p4, int p5) {
        return intPenta.get(new PentaKey(fn, p1, p2, p3, p4, p5));
    }

    public void putInt5(String fn, int p1, int p2, int p3, int p4, int p5, int result) {
        var key = new PentaKey(fn, p1, p2, p3, p4, p5);
        intPenta.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
        if (p4 != p1 && p4 != p2 && p4 != p3) addToReverseIndex(p4, key);
        if (p5 != p1 && p5 != p2 && p5 != p3 && p5 != p4) addToReverseIndex(p5, key);
    }

    // ========== Long (ToLong) Methods ==========

    public Long getLong1(String fn, int p1) {
        return longUnary.get(new UnaryKey(fn, p1));
    }

    public void putLong1(String fn, int p1, long result) {
        var key = new UnaryKey(fn, p1);
        longUnary.put(key, result);
        addToReverseIndex(p1, key);
    }

    public Long getLong2(String fn, int p1, int p2) {
        return longBinary.get(new BinaryKey(fn, p1, p2));
    }

    public void putLong2(String fn, int p1, int p2, long result) {
        var key = new BinaryKey(fn, p1, p2);
        longBinary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
    }

    public Long getLong3(String fn, int p1, int p2, int p3) {
        return longTernary.get(new TernaryKey(fn, p1, p2, p3));
    }

    public void putLong3(String fn, int p1, int p2, int p3, long result) {
        var key = new TernaryKey(fn, p1, p2, p3);
        longTernary.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
    }

    public Long getLong4(String fn, int p1, int p2, int p3, int p4) {
        return longQuad.get(new QuadKey(fn, p1, p2, p3, p4));
    }

    public void putLong4(String fn, int p1, int p2, int p3, int p4, long result) {
        var key = new QuadKey(fn, p1, p2, p3, p4);
        longQuad.put(key, result);
        addToReverseIndex(p1, key);
        if (p2 != p1) addToReverseIndex(p2, key);
        if (p3 != p1 && p3 != p2) addToReverseIndex(p3, key);
        if (p4 != p1 && p4 != p2 && p4 != p3) addToReverseIndex(p4, key);
    }

    // ========== Reverse Index Management ==========

    private void addToReverseIndex(int pointer, Object key) {
        keysByPointer.computeIfAbsent(pointer, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    /**
     * Invalidate all cached results involving the given entity pointer.
     * O(1) lookup via reverse index.
     */
    public void invalidateEntity(int pointer) {
        var keys = keysByPointer.remove(pointer);
        if (keys == null) return;

        for (var key : keys) {
            switch (key) {
                case UnaryKey k -> {
                    boolUnary.remove(k);
                    intUnary.remove(k);
                    longUnary.remove(k);
                }
                case BinaryKey k -> {
                    boolBinary.remove(k);
                    intBinary.remove(k);
                    longBinary.remove(k);
                    // Remove from other pointer's reverse index
                    int other = (k.p1 == pointer) ? k.p2 : k.p1;
                    var otherSet = keysByPointer.get(other);
                    if (otherSet != null) otherSet.remove(k);
                }
                case TernaryKey k -> {
                    boolTernary.remove(k);
                    intTernary.remove(k);
                    longTernary.remove(k);
                    removeFromOtherIndices(pointer, k, k.p1, k.p2, k.p3);
                }
                case QuadKey k -> {
                    boolQuad.remove(k);
                    intQuad.remove(k);
                    longQuad.remove(k);
                    removeFromOtherIndices(pointer, k, k.p1, k.p2, k.p3, k.p4);
                }
                case PentaKey k -> {
                    boolPenta.remove(k);
                    intPenta.remove(k);
                    removeFromOtherIndices(pointer, k, k.p1, k.p2, k.p3, k.p4, k.p5);
                }
                default -> {}
            }
        }
        version++;
    }

    private void removeFromOtherIndices(int pointer, Object key, int... pointers) {
        for (int p : pointers) {
            if (p != pointer) {
                var set = keysByPointer.get(p);
                if (set != null) set.remove(key);
            }
        }
    }

    public void clear() {
        boolUnary.clear();
        boolBinary.clear();
        boolTernary.clear();
        boolQuad.clear();
        boolPenta.clear();
        intUnary.clear();
        intBinary.clear();
        intTernary.clear();
        intQuad.clear();
        intPenta.clear();
        longUnary.clear();
        longBinary.clear();
        longTernary.clear();
        longQuad.clear();
        keysByPointer.clear();
        version++;
    }

    public long getVersion() {
        return version;
    }

    public String getStats() {
        int boolCount = boolUnary.size() + boolBinary.size() + boolTernary.size() + boolQuad.size() + boolPenta.size();
        int intCount = intUnary.size() + intBinary.size() + intTernary.size() + intQuad.size() + intPenta.size();
        int longCount = longUnary.size() + longBinary.size() + longTernary.size() + longQuad.size();
        return String.format("FunctionCache[bool=%d, int=%d, long=%d, version=%d]", boolCount, intCount, longCount, version);
    }
}
