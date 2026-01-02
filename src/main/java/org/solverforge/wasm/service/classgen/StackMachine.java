package org.solverforge.wasm.service.classgen;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stack machine abstraction for JVM bytecode generation.
 *
 * This provides a principled way to generate bytecode by tracking stack state
 * and validating operations. Instead of reasoning about individual bytecode
 * instructions, you work with typed stack values and high-level operations.
 *
 * The stack machine ensures:
 * - Type safety: operations validate their operands
 * - Stack balance: branches merge with consistent stack states
 * - Clear errors: failures report what went wrong and where
 */
public class StackMachine {

    /**
     * Represents the computational type category of a JVM value.
     * Category 1: int, float, reference (32-bit slots)
     * Category 2: long, double (64-bit, occupy 2 slots conceptually)
     */
    public enum Category {
        CAT1(1),  // int, float, reference
        CAT2(2);  // long, double

        public final int slots;

        Category(int slots) {
            this.slots = slots;
        }
    }

    /**
     * Represents the type of a value on the operand stack.
     */
    public enum StackType {
        INT(Category.CAT1, TypeKind.INT),
        LONG(Category.CAT2, TypeKind.LONG),
        FLOAT(Category.CAT1, TypeKind.FLOAT),
        DOUBLE(Category.CAT2, TypeKind.DOUBLE),
        REFERENCE(Category.CAT1, TypeKind.REFERENCE),
        /** Uninitialized reference from 'new' instruction, before {@code <init>} */
        UNINITIALIZED(Category.CAT1, TypeKind.REFERENCE);

        public final Category category;
        public final TypeKind typeKind;

        StackType(Category category, TypeKind typeKind) {
            this.category = category;
            this.typeKind = typeKind;
        }

        public boolean isCategory1() {
            return category == Category.CAT1;
        }

        public boolean isCategory2() {
            return category == Category.CAT2;
        }

        public static StackType fromTypeKind(TypeKind kind) {
            return switch (kind) {
                case INT, BYTE, SHORT, CHAR, BOOLEAN -> INT;
                case LONG -> LONG;
                case FLOAT -> FLOAT;
                case DOUBLE -> DOUBLE;
                case REFERENCE -> REFERENCE;
                case VOID -> throw new IllegalArgumentException("void is not a stack type");
            };
        }

        public static StackType fromDescriptor(ClassDesc desc) {
            String d = desc.descriptorString();
            return switch (d.charAt(0)) {
                case 'I', 'B', 'S', 'C', 'Z' -> INT;
                case 'J' -> LONG;
                case 'F' -> FLOAT;
                case 'D' -> DOUBLE;
                case 'L', '[' -> REFERENCE;
                default -> throw new IllegalArgumentException("Unknown descriptor: " + d);
            };
        }
    }

    /**
     * Represents a value on the operand stack with its type and optional debug info.
     */
    public record StackValue(StackType type, String description) {
        public StackValue(StackType type) {
            this(type, type.name().toLowerCase());
        }

        public boolean isCategory1() {
            return type.isCategory1();
        }

        public boolean isCategory2() {
            return type.isCategory2();
        }

        @Override
        public String toString() {
            return description + ":" + type;
        }
    }

    private final CodeBuilder code;
    private final Deque<StackValue> stack = new ArrayDeque<>();
    private final Map<Label, List<StackValue>> labelStates = new HashMap<>();
    private final String methodName;
    private int operationCount = 0;

    public StackMachine(CodeBuilder code, String methodName) {
        this.code = code;
        this.methodName = methodName;
    }

    // ========== Stack State Queries ==========

    public int depth() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public StackValue peek() {
        if (stack.isEmpty()) {
            throw stackError("Cannot peek: stack is empty");
        }
        return stack.peek();
    }

    public StackValue peek(int n) {
        if (n >= stack.size()) {
            throw stackError("Cannot peek(%d): stack depth is %d", n, stack.size());
        }
        var it = stack.iterator();
        for (int i = 0; i < n; i++) it.next();
        return it.next();
    }

    public List<StackValue> getState() {
        return new ArrayList<>(stack);
    }

    public String stackString() {
        if (stack.isEmpty()) return "[]";
        var sb = new StringBuilder("[");
        var it = stack.descendingIterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) sb.append(", ");
        }
        return sb.append("]").toString();
    }

    // ========== Push Operations ==========

    public StackMachine pushInt(int value) {
        code.loadConstant(value);
        push(new StackValue(StackType.INT, "const:" + value));
        return this;
    }

    public StackMachine pushLong(long value) {
        code.loadConstant(value);
        push(new StackValue(StackType.LONG, "const:" + value + "L"));
        return this;
    }

    public StackMachine pushNull() {
        code.aconst_null();
        push(new StackValue(StackType.REFERENCE, "null"));
        return this;
    }

    public StackMachine pushThis() {
        code.aload(0);
        push(new StackValue(StackType.REFERENCE, "this"));
        return this;
    }

    public StackMachine loadLocal(int slot, StackType type, String name) {
        code.loadLocal(type.typeKind, slot);
        push(new StackValue(type, "local:" + name));
        return this;
    }

    public StackMachine loadIntLocal(int slot, String name) {
        return loadLocal(slot, StackType.INT, name);
    }

    public StackMachine loadLongLocal(int slot, String name) {
        return loadLocal(slot, StackType.LONG, name);
    }

    public StackMachine loadRefLocal(int slot, String name) {
        return loadLocal(slot, StackType.REFERENCE, name);
    }

    // ========== Store Operations ==========

    public StackMachine storeLocal(int slot, StackType expectedType, String name) {
        var value = pop();
        if (value.type != expectedType) {
            throw stackError("Cannot store %s to local %s: expected %s",
                    value.type, name, expectedType);
        }
        code.storeLocal(expectedType.typeKind, slot);
        return this;
    }

    public StackMachine storeIntLocal(int slot, String name) {
        return storeLocal(slot, StackType.INT, name);
    }

    public StackMachine storeLongLocal(int slot, String name) {
        return storeLocal(slot, StackType.LONG, name);
    }

    public StackMachine storeRefLocal(int slot, String name) {
        return storeLocal(slot, StackType.REFERENCE, name);
    }

    // ========== Field Operations ==========

    public StackMachine getField(ClassDesc owner, String fieldName, ClassDesc fieldType) {
        var obj = pop();
        if (obj.type != StackType.REFERENCE) {
            throw stackError("getfield requires reference on stack, got %s", obj.type);
        }
        code.getfield(owner, fieldName, fieldType);
        push(new StackValue(StackType.fromDescriptor(fieldType), "field:" + fieldName));
        return this;
    }

    public StackMachine putField(ClassDesc owner, String fieldName, ClassDesc fieldType) {
        var value = pop();
        var obj = pop();

        if (obj.type != StackType.REFERENCE && obj.type != StackType.UNINITIALIZED) {
            throw stackError("putfield requires reference as receiver, got %s", obj.type);
        }

        var expectedType = StackType.fromDescriptor(fieldType);
        if (value.type != expectedType && !(value.type == StackType.REFERENCE && expectedType == StackType.REFERENCE)) {
            throw stackError("putfield %s expects %s, got %s", fieldName, expectedType, value.type);
        }

        code.putfield(owner, fieldName, fieldType);
        return this;
    }

    public StackMachine getStatic(ClassDesc owner, String fieldName, ClassDesc fieldType) {
        code.getstatic(owner, fieldName, fieldType);
        push(new StackValue(StackType.fromDescriptor(fieldType), "static:" + fieldName));
        return this;
    }

    // ========== Method Invocations ==========

    public StackMachine invokeVirtual(ClassDesc owner, String methodName, MethodTypeDesc methodType) {
        return invoke(InvokeKind.VIRTUAL, owner, methodName, methodType);
    }

    public StackMachine invokeInterface(ClassDesc owner, String methodName, MethodTypeDesc methodType) {
        return invoke(InvokeKind.INTERFACE, owner, methodName, methodType);
    }

    public StackMachine invokeStatic(ClassDesc owner, String methodName, MethodTypeDesc methodType) {
        return invoke(InvokeKind.STATIC, owner, methodName, methodType);
    }

    public StackMachine invokeSpecial(ClassDesc owner, String methodName, MethodTypeDesc methodType) {
        return invoke(InvokeKind.SPECIAL, owner, methodName, methodType);
    }

    private enum InvokeKind { VIRTUAL, INTERFACE, STATIC, SPECIAL }

    private StackMachine invoke(InvokeKind kind, ClassDesc owner, String methodName, MethodTypeDesc methodType) {
        // Pop arguments in reverse order
        var paramCount = methodType.parameterCount();
        for (int i = paramCount - 1; i >= 0; i--) {
            var expectedType = StackType.fromDescriptor(methodType.parameterType(i));
            var actual = pop();
            if (actual.type != expectedType && !(actual.type == StackType.REFERENCE && expectedType == StackType.REFERENCE)) {
                throw stackError("invoke %s.%s: param %d expected %s, got %s",
                        owner.displayName(), methodName, i, expectedType, actual.type);
            }
        }

        // Pop receiver for non-static methods
        if (kind != InvokeKind.STATIC) {
            var receiver = pop();
            if (receiver.type != StackType.REFERENCE && receiver.type != StackType.UNINITIALIZED) {
                throw stackError("invoke %s.%s requires reference receiver, got %s",
                        owner.displayName(), methodName, receiver.type);
            }

            // Special case: invokespecial <init> converts UNINITIALIZED to REFERENCE
            if (kind == InvokeKind.SPECIAL && methodName.equals("<init>")) {
                convertUninitializedToInitialized();
            }
        }

        // Emit the instruction
        switch (kind) {
            case VIRTUAL -> code.invokevirtual(owner, methodName, methodType);
            case INTERFACE -> code.invokeinterface(owner, methodName, methodType);
            case STATIC -> code.invokestatic(owner, methodName, methodType);
            case SPECIAL -> code.invokespecial(owner, methodName, methodType);
        }

        // Push return value if non-void
        var returnType = methodType.returnType();
        if (!returnType.descriptorString().equals("V")) {
            push(new StackValue(StackType.fromDescriptor(returnType), "result:" + methodName));
        }

        return this;
    }

    /** After invokespecial <init>, convert all UNINITIALIZED refs to REFERENCE */
    private void convertUninitializedToInitialized() {
        var newStack = new ArrayDeque<StackValue>();
        for (var value : stack) {
            if (value.type == StackType.UNINITIALIZED) {
                newStack.addLast(new StackValue(StackType.REFERENCE, value.description.replace("uninit:", "")));
            } else {
                newStack.addLast(value);
            }
        }
        stack.clear();
        stack.addAll(newStack);
    }

    // ========== Object Creation ==========

    public StackMachine newObject(ClassDesc type) {
        code.new_(type);
        push(new StackValue(StackType.UNINITIALIZED, "uninit:" + type.displayName()));
        return this;
    }

    public StackMachine newArray(TypeKind elementType) {
        var size = pop();
        if (size.type != StackType.INT) {
            throw stackError("newarray size must be int, got %s", size.type);
        }
        code.newarray(elementType);
        push(new StackValue(StackType.REFERENCE, "array:" + elementType));
        return this;
    }

    // ========== Array Operations ==========

    public StackMachine arrayLength() {
        var array = pop();
        if (array.type != StackType.REFERENCE) {
            throw stackError("arraylength requires array reference, got %s", array.type);
        }
        code.arraylength();
        push(new StackValue(StackType.INT, "length"));
        return this;
    }

    public StackMachine arrayLoad(StackType elementType) {
        var index = pop();
        var array = pop();
        if (index.type != StackType.INT) {
            throw stackError("array index must be int, got %s", index.type);
        }
        if (array.type != StackType.REFERENCE) {
            throw stackError("array load requires array reference, got %s", array.type);
        }

        switch (elementType) {
            case INT -> code.iaload();
            case LONG -> code.laload();
            case FLOAT -> code.faload();
            case DOUBLE -> code.daload();
            case REFERENCE -> code.aaload();
            default -> throw new IllegalArgumentException("Cannot load array element of type: " + elementType);
        }
        push(new StackValue(elementType, "elem"));
        return this;
    }

    public StackMachine arrayStore(StackType elementType) {
        var value = pop();
        var index = pop();
        var array = pop();

        if (index.type != StackType.INT) {
            throw stackError("array index must be int, got %s", index.type);
        }
        if (array.type != StackType.REFERENCE) {
            throw stackError("array store requires array reference, got %s", array.type);
        }
        if (value.type != elementType) {
            throw stackError("array store expects %s, got %s", elementType, value.type);
        }

        switch (elementType) {
            case INT -> code.iastore();
            case LONG -> code.lastore();
            case FLOAT -> code.fastore();
            case DOUBLE -> code.dastore();
            case REFERENCE -> code.aastore();
            default -> throw new IllegalArgumentException("Cannot store array element of type: " + elementType);
        }
        return this;
    }

    // ========== Stack Manipulation ==========

    /**
     * Duplicate the top value (must be category 1).
     */
    public StackMachine dup() {
        var top = peek();
        if (!top.isCategory1()) {
            throw stackError("dup requires category-1 value, got %s (category-2)", top.type);
        }
        code.dup();
        push(new StackValue(top.type, top.description + "'"));
        return this;
    }

    /**
     * Duplicate the top value (category 2) or top two values (both category 1).
     */
    public StackMachine dup2() {
        var top = peek();
        if (top.isCategory2()) {
            // Form 2: duplicate one category-2 value
            code.dup2();
            push(new StackValue(top.type, top.description + "'"));
        } else {
            // Form 1: duplicate two category-1 values
            var second = peek(1);
            if (!second.isCategory1()) {
                throw stackError("dup2 form 1 requires two category-1 values, got %s, %s", top.type, second.type);
            }
            code.dup2();
            push(new StackValue(second.type, second.description + "'"));
            push(new StackValue(top.type, top.description + "'"));
        }
        return this;
    }

    /**
     * Duplicate top category-1 value and insert below second value.
     * Stack: ..., v2, v1 -> ..., v1, v2, v1
     */
    public StackMachine dup_x1() {
        var v1 = peek();
        var v2 = peek(1);
        if (!v1.isCategory1() || !v2.isCategory1()) {
            throw stackError("dup_x1 requires two category-1 values, got %s, %s", v1.type, v2.type);
        }
        code.dup_x1();
        // Conceptually: pop v1, pop v2, push v1, push v2, push v1
        pop(); pop();
        push(new StackValue(v1.type, v1.description + "'"));
        push(v2);
        push(v1);
        return this;
    }

    /**
     * Duplicate top value and insert 2 or 3 values down.
     * Form 1: v1 is cat-1, v2 is cat-1, v3 is cat-1
     *         ..., v3, v2, v1 -> ..., v1, v3, v2, v1
     * Form 2: v1 is cat-1, v2 is cat-2
     *         ..., v2, v1 -> ..., v1, v2, v1
     */
    public StackMachine dup_x2() {
        var v1 = peek();
        if (!v1.isCategory1()) {
            throw stackError("dup_x2 requires category-1 value on top, got %s", v1.type);
        }

        var v2 = peek(1);
        if (v2.isCategory2()) {
            // Form 2: v1 is cat-1, v2 is cat-2
            code.dup_x2();
            pop(); pop(); // v1, v2
            push(new StackValue(v1.type, v1.description + "'"));
            push(v2);
            push(v1);
        } else {
            // Form 1: v1, v2, v3 all cat-1
            var v3 = peek(2);
            if (!v3.isCategory1()) {
                throw stackError("dup_x2 form 1 requires three category-1 values, v3 is %s", v3.type);
            }
            code.dup_x2();
            pop(); pop(); pop(); // v1, v2, v3
            push(new StackValue(v1.type, v1.description + "'"));
            push(v3);
            push(v2);
            push(v1);
        }
        return this;
    }

    /**
     * Swap the top two category-1 values.
     * Stack: ..., v2, v1 -> ..., v1, v2
     */
    public StackMachine swap() {
        var v1 = peek();
        var v2 = peek(1);
        if (!v1.isCategory1() || !v2.isCategory1()) {
            throw stackError("swap requires two category-1 values, got %s, %s", v1.type, v2.type);
        }
        code.swap();
        pop(); pop();
        push(v1);
        push(v2);
        return this;
    }

    /**
     * Pop the top value (category 1).
     */
    public StackMachine pop1() {
        var top = peek();
        if (!top.isCategory1()) {
            throw stackError("pop requires category-1 value, got %s", top.type);
        }
        code.pop();
        pop();
        return this;
    }

    /**
     * Pop one category-2 value or two category-1 values.
     */
    public StackMachine pop2() {
        var top = peek();
        if (top.isCategory2()) {
            code.pop2();
            pop();
        } else {
            var second = peek(1);
            if (!second.isCategory1()) {
                throw stackError("pop2 with cat-1 on top requires another cat-1 below, got %s", second.type);
            }
            code.pop2();
            pop(); pop();
        }
        return this;
    }

    // ========== Type Conversions ==========

    public StackMachine i2l() {
        var v = pop();
        if (v.type != StackType.INT) {
            throw stackError("i2l requires int, got %s", v.type);
        }
        code.i2l();
        push(new StackValue(StackType.LONG, v.description + "->long"));
        return this;
    }

    public StackMachine l2i() {
        var v = pop();
        if (v.type != StackType.LONG) {
            throw stackError("l2i requires long, got %s", v.type);
        }
        code.l2i();
        push(new StackValue(StackType.INT, v.description + "->int"));
        return this;
    }

    public StackMachine checkcast(ClassDesc type) {
        var v = pop();
        if (v.type != StackType.REFERENCE) {
            throw stackError("checkcast requires reference, got %s", v.type);
        }
        code.checkcast(type);
        push(new StackValue(StackType.REFERENCE, type.displayName()));
        return this;
    }

    // ========== Comparisons ==========

    public StackMachine lcmp() {
        var v2 = pop();
        var v1 = pop();
        if (v1.type != StackType.LONG || v2.type != StackType.LONG) {
            throw stackError("lcmp requires two longs, got %s, %s", v1.type, v2.type);
        }
        code.lcmp();
        push(new StackValue(StackType.INT, "cmp"));
        return this;
    }

    // ========== Control Flow ==========

    public Label newLabel() {
        return code.newLabel();
    }

    public StackMachine labelBinding(Label label) {
        // Check if this label was previously jumped to
        var savedState = labelStates.get(label);

        if (unreachable) {
            // We came from unreachable code (after goto/return/throw)
            // Restore stack state from saved state
            if (savedState != null) {
                stack.clear();
                for (int i = savedState.size() - 1; i >= 0; i--) {
                    stack.push(savedState.get(i));
                }
            }
            unreachable = false;
        } else if (savedState != null) {
            // Verify stack state matches (we're falling through)
            var currentState = getState();
            if (!stackStatesMatch(savedState, currentState)) {
                throw stackError("Label stack mismatch: expected %s, got %s", savedState, currentState);
            }
        }

        code.labelBinding(label);
        return this;
    }

    public StackMachine ifne(Label target) {
        var v = pop();
        if (v.type != StackType.INT) {
            throw stackError("ifne requires int, got %s", v.type);
        }
        saveLabelState(target);
        code.ifne(target);
        return this;
    }

    public StackMachine ifeq(Label target) {
        var v = pop();
        if (v.type != StackType.INT) {
            throw stackError("ifeq requires int, got %s", v.type);
        }
        saveLabelState(target);
        code.ifeq(target);
        return this;
    }

    public StackMachine if_icmpne(Label target) {
        var v2 = pop();
        var v1 = pop();
        if (v1.type != StackType.INT || v2.type != StackType.INT) {
            throw stackError("if_icmpne requires two ints, got %s, %s", v1.type, v2.type);
        }
        saveLabelState(target);
        code.if_icmpne(target);
        return this;
    }

    public StackMachine if_acmpne(Label target) {
        var v2 = pop();
        var v1 = pop();
        if (v1.type != StackType.REFERENCE || v2.type != StackType.REFERENCE) {
            throw stackError("if_acmpne requires two references, got %s, %s", v1.type, v2.type);
        }
        saveLabelState(target);
        code.if_acmpne(target);
        return this;
    }

    public StackMachine goto_(Label target) {
        saveLabelState(target);
        code.goto_(target);
        // After unconditional jump, mark stack as unreachable until next label binding
        unreachable = true;
        return this;
    }

    private boolean unreachable = false;

    private void saveLabelState(Label label) {
        var existing = labelStates.get(label);
        if (existing != null) {
            var current = getState();
            if (!stackStatesMatch(existing, current)) {
                throw stackError("Branch to label with inconsistent stack: expected %s, got %s", existing, current);
            }
        } else {
            labelStates.put(label, getState());
        }
    }

    private boolean stackStatesMatch(List<StackValue> a, List<StackValue> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).type != b.get(i).type) return false;
        }
        return true;
    }

    // ========== Return ==========

    public void returnVoid() {
        if (!stack.isEmpty()) {
            throw stackError("return void but stack is not empty: %s", stackString());
        }
        code.return_();
    }

    public void returnInt() {
        var v = pop();
        if (v.type != StackType.INT) {
            throw stackError("ireturn requires int, got %s", v.type);
        }
        if (!stack.isEmpty()) {
            throw stackError("ireturn but stack has extra values: %s", stackString());
        }
        code.ireturn();
    }

    public void returnLong() {
        var v = pop();
        if (v.type != StackType.LONG) {
            throw stackError("lreturn requires long, got %s", v.type);
        }
        if (!stack.isEmpty()) {
            throw stackError("lreturn but stack has extra values: %s", stackString());
        }
        code.lreturn();
    }

    public void returnRef() {
        var v = pop();
        if (v.type != StackType.REFERENCE) {
            throw stackError("areturn requires reference, got %s", v.type);
        }
        if (!stack.isEmpty()) {
            throw stackError("areturn but stack has extra values: %s", stackString());
        }
        code.areturn();
    }

    public void returnValue(TypeKind kind) {
        switch (kind) {
            case VOID -> returnVoid();
            case INT, BYTE, SHORT, CHAR, BOOLEAN -> returnInt();
            case LONG -> returnLong();
            case FLOAT -> {
                var v = pop();
                if (v.type != StackType.FLOAT) {
                    throw stackError("freturn requires float, got %s", v.type);
                }
                if (!stack.isEmpty()) {
                    throw stackError("freturn but stack has extra values: %s", stackString());
                }
                code.freturn();
            }
            case DOUBLE -> {
                var v = pop();
                if (v.type != StackType.DOUBLE) {
                    throw stackError("dreturn requires double, got %s", v.type);
                }
                if (!stack.isEmpty()) {
                    throw stackError("dreturn but stack has extra values: %s", stackString());
                }
                code.dreturn();
            }
            case REFERENCE -> returnRef();
        }
    }

    // ========== Raw CodeBuilder Access (escape hatch) ==========

    /**
     * Access the underlying CodeBuilder for operations not yet wrapped.
     * Use sparingly - prefer adding proper wrapper methods.
     */
    public CodeBuilder raw() {
        return code;
    }

    /**
     * Manually record a value pushed via raw() operations.
     * Use when you need raw() but want to keep stack tracking accurate.
     */
    public StackMachine stack_push(StackType type, String description) {
        push(new StackValue(type, description));
        return this;
    }

    /**
     * Manually record a value popped via raw() operations.
     * Use when you need raw() but want to keep stack tracking accurate.
     */
    public StackMachine stack_pop() {
        pop();
        return this;
    }

    // ========== Internal Helpers ==========

    private void push(StackValue value) {
        stack.push(value);
        operationCount++;
    }

    private StackValue pop() {
        if (stack.isEmpty()) {
            throw stackError("Cannot pop: stack is empty");
        }
        operationCount++;
        return stack.pop();
    }

    private IllegalStateException stackError(String format, Object... args) {
        var msg = String.format(format, args);
        return new IllegalStateException(String.format(
                "[%s op#%d] %s. Stack: %s",
                methodName, operationCount, msg, stackString()));
    }
}
