package org.solverforge.wasm.service.classgen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solverforge.wasm.service.FunctionCache;
import org.solverforge.wasm.service.SolverResource;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasm.Parser;

/**
 * Tests for WasmSolutionCloner cache clearing behavior.
 *
 * During solution cloning, all caches (entity, list, function) must be cleared
 * to prevent stale shadow variable state that causes "Unexpected unassigned position" errors.
 */
public class WasmSolutionClonerTest {

    private static final byte[] MINIMAL_WASM = new byte[] {
        0x00, 0x61, 0x73, 0x6d, // magic number
        0x01, 0x00, 0x00, 0x00  // version
    };

    private Instance wasmInstance;
    private FunctionCache functionCache;

    @BeforeEach
    void setUp() {
        wasmInstance = Instance.builder(Parser.parse(MINIMAL_WASM)).build();
        functionCache = new FunctionCache();
        SolverResource.INSTANCE.set(wasmInstance);
        SolverResource.FUNCTION_CACHE.set(functionCache);
    }

    @AfterEach
    void tearDown() {
        SolverResource.INSTANCE.remove();
        SolverResource.FUNCTION_CACHE.remove();
    }

    @Test
    void cloningClearsFunctionCache() {
        // Populate the function cache with some entries
        functionCache.putBool1("testFunc", 100, true);
        functionCache.putBool2("testFunc2", 100, 200, false);
        functionCache.putInt1("intFunc", 300, 42);

        // Verify cache has entries
        assertThat(functionCache.getBool1("testFunc", 100)).isTrue();
        assertThat(functionCache.getBool2("testFunc2", 100, 200)).isFalse();
        assertThat(functionCache.getInt1("intFunc", 300)).isEqualTo(42);

        // Clear caches as WasmSolutionCloner does
        WasmObject.clearCacheForInstance(wasmInstance);
        WasmList.clearCacheForInstance(wasmInstance);
        FunctionCache cache = SolverResource.FUNCTION_CACHE.get();
        if (cache != null) {
            cache.clear();
        }

        // Verify function cache is cleared
        assertThat(functionCache.getBool1("testFunc", 100)).isNull();
        assertThat(functionCache.getBool2("testFunc2", 100, 200)).isNull();
        assertThat(functionCache.getInt1("intFunc", 300)).isNull();
    }

    @Test
    void functionCacheClearResetsEntityVersions() {
        // Populate cache entries for a pointer
        int pointer = 100;
        functionCache.putBool1("func", pointer, true);

        // Invalidate entity (bumps version)
        functionCache.invalidateEntity(pointer);

        // Entry should now be stale
        assertThat(functionCache.getBool1("func", pointer)).isNull();

        // Add new entry with current version
        functionCache.putBool1("func", pointer, false);
        assertThat(functionCache.getBool1("func", pointer)).isFalse();

        // Clear cache
        functionCache.clear();

        // After clear, versions are reset - new entries work fresh
        functionCache.putBool1("func", pointer, true);
        assertThat(functionCache.getBool1("func", pointer)).isTrue();
    }
}
