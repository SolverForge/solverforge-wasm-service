package ai.timefold.wasm.service.classgen;

import java.util.function.IntFunction;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;

public class WasmObject {
    public final Instance wasmInstance;
    public final int memoryPointer;

    public WasmObject() {
        // Required for cloning
        memoryPointer = 0;
        wasmInstance = null;
    }

    public WasmObject(Allocator allocator, Instance wasmInstance, int size) {
        this.wasmInstance = wasmInstance;
        memoryPointer = allocator.allocate(size);
    }

    public WasmObject(Instance wasmInstance, int memoryPointer) {
        this.wasmInstance = wasmInstance;
        this.memoryPointer = memoryPointer;
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

    public static WasmObject ofExisting(Instance wasmInstance,
            int memoryPointer) {
        return new WasmObject(wasmInstance, memoryPointer);
    }

    public static WasmObject ofExistingOrDefault(Instance wasmInstance,
            int memoryPointer, WasmObject defaultValue) {
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static <Item_ extends WasmObject> Item_ ofExistingOrCreate(Instance wasmInstance,
            int memoryPointer, IntFunction<Item_> factory) {
        return factory.apply(memoryPointer);
    }

    @Override
    public String toString() {
        return "%s(pointer=%x)".formatted(getClass().getSimpleName(), memoryPointer);
    }
}
