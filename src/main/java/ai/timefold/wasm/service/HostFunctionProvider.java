package ai.timefold.wasm.service;

import java.util.List;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides host functions required by WASM modules for solving planning problems.
 *
 * These functions bridge between the WASM runtime and Java, handling:
 * - JSON parsing/serialization of problem data
 * - List operations (allocation, access, modification)
 * - Utility functions (rounding)
 *
 * The functions are imported by WASM modules under the "host" namespace.
 */
public class HostFunctionProvider {
    private static final int WORD_SIZE = Integer.SIZE;
    private final ObjectMapper objectMapper;

    public HostFunctionProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates all host functions needed by the WASM module.
     *
     * @return List of host functions to be imported into the WASM instance
     */
    public List<HostFunction> createHostFunctions() {
        return List.of(
                createParseSchedule(),
                createScheduleString(),
                createNewList(),
                createGetItem(),
                createSetItem(),
                createSize(),
                createAppend(),
                createInsert(),
                createRemove(),
                createRound()
        );
    }

    /**
     * hparseSchedule(length: i32, ptr: i32) -> i32
     *
     * Parses a JSON schedule string from WASM memory and creates native WASM objects.
     * Returns pointer to the allocated schedule structure.
     */
    private HostFunction createParseSchedule() {
        return new HostFunction("host", "hparseSchedule",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var scheduleString = instance.memory().readString((int) args[1], (int) args[0]);
                    var alloc = instance.export("alloc");
                    var newList = instance.export("newList");
                    var append = instance.export("append");
                    var getItem = instance.export("getItem");

                    try {
                        var parsedSchedule = objectMapper.reader().readTree(scheduleString);
                        var parsedEmployees = parsedSchedule.get("employees");
                        var parsedShifts = parsedSchedule.get("shifts");

                        var schedule = (int) alloc.apply(WORD_SIZE * 2)[0];
                        var employees = (int) newList.apply()[0];
                        var shifts = (int) newList.apply()[0];

                        instance.memory().writeI32(schedule, employees);
                        instance.memory().writeI32(schedule + WORD_SIZE, shifts);

                        for (var i = 0; i < parsedEmployees.size(); i++) {
                            var parsedEmployee = parsedEmployees.get(i);
                            var id = parsedEmployee.get("id").asInt();
                            var employee = (int) alloc.apply(WORD_SIZE)[0];
                            instance.memory().writeI32(employee, id);
                            append.apply(employees, employee);
                        }

                        for (var i = 0; i < parsedShifts.size(); i++) {
                            var parsedShift = parsedShifts.get(i);
                            var shift = (int) alloc.apply(WORD_SIZE)[0];

                            if (parsedShift.has("employee")) {
                                var employee = parsedShift.get("employee");
                                if (employee.isNull()) {
                                    instance.memory().writeI32(shift, 0);
                                } else {
                                    instance.memory().writeI32(shift,
                                            (int) getItem.apply(employees, employee.get("id").asInt())[0]);
                                }
                            }

                            append.apply(shifts, shift);
                        }

                        return new long[] { schedule };
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * hscheduleString(schedule: i32) -> i32
     *
     * Serializes a WASM schedule object back to JSON string.
     * Returns pointer to the allocated string in WASM memory.
     */
    private HostFunction createScheduleString() {
        return new HostFunction("host", "hscheduleString",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var schedule = (int) args[0];
                    var alloc = instance.export("alloc");
                    var listSize = instance.export("size");
                    var getItem = instance.export("getItem");

                    var employees = instance.memory().readI32(schedule);
                    var employeesLength = (int) listSize.apply(employees)[0];
                    var shifts = instance.memory().readI32(schedule + WORD_SIZE);
                    var shiftsLength = (int) listSize.apply(shifts)[0];

                    StringBuilder out = new StringBuilder();
                    out.append("{");

                    var isFirst = true;
                    out.append("\"employees\": [");
                    for (int i = 0; i < employeesLength; i++) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            out.append(", ");
                        }
                        out.append("{");
                        out.append("\"id\": ");
                        var id = instance.memory().readI32((int) getItem.apply(employees, i)[0]);
                        out.append(id);
                        out.append("}");
                    }
                    out.append("], ");

                    isFirst = true;
                    out.append("\"shifts\": [");
                    for (int i = 0; i < shiftsLength; i++) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            out.append(", ");
                        }
                        out.append("{\"employee\": ");
                        var employee = (int) instance.memory().readI32((int) getItem.apply(shifts, i)[0]);
                        if (employee == 0) {
                            out.append("null");
                        } else {
                            out.append("{");
                            out.append("\"id\": ");
                            var id = instance.memory().readI32(employee);
                            out.append(id);
                            out.append("}");
                        }
                        out.append("}");
                    }
                    out.append("]}");
                    var outString = out.toString();
                    var memoryString = (int) alloc.apply(outString.getBytes().length + 1)[0];
                    instance.memory().writeCString(memoryString, outString);
                    return new long[] { memoryString };
                });
    }

    /**
     * hnewList() -> i32
     *
     * Allocates a new empty list structure in WASM memory.
     * List structure: [size: i32][backing_array_ptr: i32]
     */
    private HostFunction createNewList() {
        return new HostFunction("host", "hnewList",
                FunctionType.of(List.of(), List.of(ValType.I32)),
                (instance, args) -> {
                    var alloc = instance.export("alloc");
                    var listInstance = (int) alloc.apply(WORD_SIZE * 2)[0];

                    instance.memory().writeI32(listInstance, 0);
                    instance.memory().writeI32(listInstance + WORD_SIZE, 0);

                    return new long[] { listInstance };
                });
    }

    /**
     * hgetItem(list: i32, index: i32) -> i32
     *
     * Gets an item from a list at the specified index.
     */
    private HostFunction createGetItem() {
        return new HostFunction("host", "hgetItem",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var listInstance = (int) args[0];
                    var backingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                    int itemIndex = (int) args[1];
                    var item = instance.memory().readI32(backingArray + (WORD_SIZE * itemIndex));

                    return new long[] { item };
                });
    }

    /**
     * hsetItem(list: i32, index: i32, item: i32)
     *
     * Sets an item in a list at the specified index.
     */
    private HostFunction createSetItem() {
        return new HostFunction("host", "hsetItem",
                FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    var listInstance = (int) args[0];
                    var backingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                    int itemIndex = (int) args[1];
                    var item = (int) args[2];
                    instance.memory().writeI32(backingArray + (WORD_SIZE * itemIndex), item);
                    return new long[] {};
                });
    }

    /**
     * hsize(list: i32) -> i32
     *
     * Returns the size of a list.
     */
    private HostFunction createSize() {
        return new HostFunction("host", "hsize",
                FunctionType.of(List.of(ValType.I32), List.of(ValType.I32)),
                (instance, args) -> {
                    var size = (int) instance.memory().readI32((int) args[0]);
                    return new long[] { size };
                });
    }

    /**
     * happend(list: i32, item: i32)
     *
     * Appends an item to the end of a list.
     * Reallocates the backing array to accommodate the new item.
     */
    private HostFunction createAppend() {
        return new HostFunction("host", "happend",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    var alloc = instance.export("alloc");

                    var listInstance = (int) args[0];

                    var oldSize = (int) instance.memory().readI32(listInstance);
                    var newSize = oldSize + 1;

                    instance.memory().writeI32(listInstance, newSize);

                    var oldBackingArray = (int) instance.memory().readI32(listInstance + WORD_SIZE);
                    var newBackingArray = (int) alloc.apply((long) WORD_SIZE * newSize)[0];

                    instance.memory().copy(newBackingArray, oldBackingArray, oldSize * WORD_SIZE);
                    instance.memory().writeI32(newBackingArray + oldSize * WORD_SIZE, (int) args[1]);
                    instance.memory().writeI32(listInstance + WORD_SIZE, newBackingArray);

                    return new long[] {};
                });
    }

    /**
     * hinsert(list: i32, index: i32, item: i32)
     *
     * Inserts an item at the specified index in a list.
     * Not implemented - only needed for list planning variables.
     */
    private HostFunction createInsert() {
        return new HostFunction("host", "hinsert",
                FunctionType.of(List.of(ValType.I32, ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    // Not needed for simple planning variables
                    throw new UnsupportedOperationException();
                });
    }

    /**
     * hremove(list: i32, index: i32)
     *
     * Removes an item at the specified index from a list.
     * Not implemented - only needed for list planning variables.
     */
    private HostFunction createRemove() {
        return new HostFunction("host", "hremove",
                FunctionType.of(List.of(ValType.I32, ValType.I32), List.of()),
                (instance, args) -> {
                    // Not needed for simple planning variables
                    throw new UnsupportedOperationException();
                });
    }

    /**
     * hround(value: f32) -> i32
     *
     * Rounds a float value multiplied by 10 to an integer.
     * Used for score calculations with decimal weights.
     */
    private HostFunction createRound() {
        return new HostFunction("host", "hround",
                FunctionType.of(List.of(ValType.F32), List.of(ValType.I32)),
                (instance, args) -> new long[] { (long) (Float.intBitsToFloat((int) args[0]) * 10) });
    }
}
