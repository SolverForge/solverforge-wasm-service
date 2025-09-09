package ai.timefold.wasm.service.classgen;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractList;
import java.util.function.IntFunction;

import ai.timefold.solver.core.impl.domain.solution.cloner.PlanningCloneable;

import com.dylibso.chicory.runtime.Instance;

public final class WasmList<Item_ extends WasmObject> extends AbstractList<Item_> implements PlanningCloneable<WasmList<Item_>> {
    private final WasmListAccessor listAccessor;
    private final WasmObject wasmList;
    private final IntFunction<Item_> itemFromPointer;

    private int cachedSize;

    public WasmList(WasmListAccessor listAccessor, WasmObject wasmList,
            Class<Item_> itemClass) {
        this.listAccessor = listAccessor;
        this.wasmList = wasmList;
        cachedSize = listAccessor.getLength(wasmList);

        var wasmInstance = listAccessor.getWasmInstance();
        try {
            var itemClassConstructor = itemClass.getConstructor(Instance.class, int.class);
            itemFromPointer = pointer -> {
                try {
                    return itemClassConstructor.newInstance(wasmInstance, pointer);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private WasmList(WasmListAccessor listAccessor, WasmObject wasmList, IntFunction<Item_> itemFromPointer) {
        this.listAccessor = listAccessor;
        this.wasmList = wasmList;
        this.itemFromPointer = itemFromPointer;
        cachedSize = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Item_ get(int index) {
        return (Item_) listAccessor.getItem(wasmList, index);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Item_ set(int index, Item_ element) {
        var old = get(index);
        listAccessor.setItem(wasmList, index, element);
        return old;
    }

    @Override
    public int size() {
        return cachedSize;
    }

    @Override
    public void add(int index, Item_ element) {
        if (index == cachedSize) {
            listAccessor.append(wasmList, element);
        } else {
            listAccessor.insert(wasmList, index, element);
        }
        cachedSize++;
    }

    @Override
    public Item_ remove(int index) {
        var old = get(index);
        listAccessor.remove(wasmList, index);
        cachedSize--;
        return old;
    }

    @Override
    public WasmList<Item_> createNewInstance() {
        return new WasmList<>(listAccessor, listAccessor.newInstance(), itemFromPointer);
    }
}
