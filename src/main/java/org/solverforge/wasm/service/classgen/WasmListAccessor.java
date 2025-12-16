package org.solverforge.wasm.service.classgen;

import java.util.Optional;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

import org.solverforge.wasm.service.dto.DomainListAccessor;

import com.dylibso.chicory.runtime.Instance;

public final class WasmListAccessor {
    @FunctionalInterface
    private interface IntBiConsumer {
        void accept(int a, int b);
    }

    @FunctionalInterface
    private interface IntTriConsumer {
        void accept(int a, int b, int c);
    }

    private final Instance wasmInstance;

    private final IntSupplier createListFunction;
    private final IntBinaryOperator getListItemFunction;
    private final IntTriConsumer setListItemFunction;
    private final IntUnaryOperator getListSizeFunction;
    private final IntBiConsumer appendListFunction;
    private final IntTriConsumer insertListFunction;
    private final IntBiConsumer removeListFunction;
    private final IntConsumer deallocListFunction;

    public WasmListAccessor(Instance instance, DomainListAccessor domainListAccessor) {
        this.wasmInstance = instance;

        var domainCreateList = Optional.ofNullable(domainListAccessor.createFunction()).map(instance::export);
        var domainGetListItem = Optional.ofNullable(domainListAccessor.getItemFunction()).map(instance::export);
        var domainSetListItem = Optional.ofNullable(domainListAccessor.setItemFunction()).map(instance::export);
        var domainGetListSize = Optional.ofNullable(domainListAccessor.getSizeFunction()).map(instance::export);
        var domainAppendListItem = Optional.ofNullable(domainListAccessor.appendFunction()).map(instance::export);
        var domainInsertListItem = Optional.ofNullable(domainListAccessor.insertFunction()).map(instance::export);
        var domainRemoveListItem = Optional.ofNullable(domainListAccessor.removeFunction()).map(instance::export);
        var domainDeallocListFunction = Optional.ofNullable(domainListAccessor.deallocator()).map(instance::export);

        createListFunction = domainCreateList.map(createList ->
                        (IntSupplier) () -> (int) createList.apply()[0])
                .orElse(() -> {
                    throw new UnsupportedOperationException("create");
                });
        getListItemFunction = domainGetListItem.map(getItem ->
                (IntBinaryOperator) (list, index) -> (int) getItem.apply(list, index)[0])
                .orElse((_, _) -> {
                    throw new UnsupportedOperationException("get");
                });
        setListItemFunction = domainSetListItem.map(setItem -> (IntTriConsumer) setItem::apply)
                .orElse((_, _, _) -> {
                    throw new UnsupportedOperationException("set");
                });
        getListSizeFunction = domainGetListSize.map(getSize -> (IntUnaryOperator) list -> (int) getSize.apply(list)[0])
                .orElse(_ -> {
                    throw new UnsupportedOperationException("size");
                });
        appendListFunction = domainAppendListItem.map(listAppend -> (IntBiConsumer) listAppend::apply)
                .orElse((_, _) -> {
                    throw new UnsupportedOperationException("append");
                });
        insertListFunction = domainInsertListItem.map(listInsert -> (IntTriConsumer) listInsert::apply)
                .orElse((_, _, _) -> {
                    throw new UnsupportedOperationException("insert");
                });
        removeListFunction = domainRemoveListItem.map(listRemove -> (IntBiConsumer) listRemove::apply)
                .orElse((_, _) -> {
                    throw new UnsupportedOperationException("remove");
                });
        deallocListFunction = domainDeallocListFunction.map(dealloc -> (IntConsumer) dealloc::apply)
                .orElse(_ -> {
                    throw new UnsupportedOperationException("dealloc");
                });
    }

    public WasmObject newInstance() {
        return WasmObject.ofExisting(wasmInstance, createListFunction.getAsInt());
    }

    public <Item_ extends WasmObject> Item_ getItem(WasmObject list, int index, IntFunction<Item_> memoryPointerToItem) {
        return WasmObject.ofExistingOrCreate(wasmInstance,
                getListItemFunction.applyAsInt(list.memoryPointer, index),
                memoryPointerToItem);
    }

    public void setItem(WasmObject list, int index, WasmObject item) {
        setListItemFunction.accept(list.memoryPointer, index, item.memoryPointer);
    }

    public int getLength(WasmObject list) {
        return getListSizeFunction.applyAsInt(list.memoryPointer);
    }

    public void append(WasmObject list, WasmObject item) {
        appendListFunction.accept(list.memoryPointer, item.memoryPointer);
    }

    public void insert(WasmObject list, int index, WasmObject item) {
        insertListFunction.accept(list.memoryPointer, index, item.memoryPointer);
    }

    public void remove(WasmObject list, int index) {
        removeListFunction.accept(list.memoryPointer, index);
    }

    public void deallocate(int memoryPointer) {
        deallocListFunction.accept(memoryPointer);
    }

    public Instance getWasmInstance() {
        return wasmInstance;
    }
}
