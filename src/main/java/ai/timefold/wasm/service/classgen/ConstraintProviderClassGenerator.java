package ai.timefold.wasm.service.classgen;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import ai.timefold.solver.core.api.function.TriFunction;
import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.wasm.service.SolverResource;
import ai.timefold.wasm.service.dto.PlanningProblem;
import ai.timefold.wasm.service.dto.WasmConstraint;
import ai.timefold.wasm.service.dto.WasmFunction;
import ai.timefold.wasm.service.dto.annotation.DomainPlanningScore;
import ai.timefold.wasm.service.dto.constraint.DataStream;
import ai.timefold.wasm.service.dto.constraint.FilterComponent;
import ai.timefold.wasm.service.dto.constraint.ForEachComponent;
import ai.timefold.wasm.service.dto.constraint.JoinComponent;
import ai.timefold.wasm.service.dto.constraint.PenalizeComponent;
import ai.timefold.wasm.service.dto.constraint.RewardComponent;

import static ai.timefold.wasm.service.classgen.DomainObjectClassGenerator.*;

import com.dylibso.chicory.runtime.Instance;

public class ConstraintProviderClassGenerator {
    private int functionCount = 0;
    private List<Consumer<Class<?>>> classInitializerList = new ArrayList<>();

    static final ClassDesc constraintProviderDesc = getDescriptor(ConstraintProvider.class);
    static final ClassDesc constraintFactoryDesc = getDescriptor(ConstraintFactory.class);

    static final ClassDesc constraintDesc = getDescriptor(Constraint.class);

    public ConstraintProviderClassGenerator() {
    }

    private static Class<?> getScoreType(PlanningProblem planningProblem) {
        var solutionDomainObject = planningProblem.getDomainObjectMap().get(planningProblem.getSolutionClass());
        for (var field : solutionDomainObject.getFieldDescriptorMap().entrySet()) {
            if (field.getValue().getAnnotations() != null) {
                for (var annotation : field.getValue().getAnnotations()) {
                    if (annotation instanceof DomainPlanningScore) {
                        return switch (field.getValue().getType()) {
                            case "SimpleScore" -> SimpleScore.class;
                            case "HardSoftScore" -> HardSoftScore.class;
                            case "HardMediumSoftScore" -> HardMediumSoftScore.class;
                            default -> {
                                throw new IllegalArgumentException("Unknown score type: " + field.getValue().getType());
                            }
                        };
                    }
                }
            }
        }
        throw new IllegalStateException("Impossible state: solution class does not have a PlanningScore annotation");
    }

    public Class<? extends ConstraintProvider> defineConstraintProviderClass(PlanningProblem planningProblem) {
        var constraintProviderClassName = "MyConstraintProvider";
        var classFile = ClassFile.of();
        var scoreType = getScoreType(planningProblem);
        var scoreDesc = getDescriptor(scoreType);
        var generatedClassDesc = ClassDesc.of(constraintProviderClassName);
        var classBytes = classFile.build(generatedClassDesc, classBuilder -> {
            classBuilder.withInterfaceSymbols(constraintProviderDesc);

            // Constructor
            classBuilder.withMethodBody("<init>", MethodTypeDesc.of(voidDesc), ClassFile.ACC_PUBLIC, codeBuilder -> {
                codeBuilder.aload(0);
                codeBuilder.invokespecial(objectDesc, "<init>", MethodTypeDesc.of(voidDesc));
                codeBuilder.return_();
            });

            // Constraints
            classBuilder.withMethodBody("defineConstraints",
                    MethodTypeDesc.of(getDescriptor(Constraint.class).arrayType(), getDescriptor(ConstraintFactory.class)), ClassFile.ACC_PUBLIC,
                    codeBuilder -> {
                        codeBuilder.loadConstant(planningProblem.getConstraintList().size());
                        codeBuilder.anewarray(getDescriptor(Constraint.class));
                        var index = 0;
                        for (var constraint : planningProblem.getConstraintList()) {
                            codeBuilder.dup();
                            codeBuilder.dup();
                            codeBuilder.loadConstant(index);

                            var dataStream = generateConstraintBody(generatedClassDesc, scoreDesc, classBuilder, codeBuilder, constraint);
                            var constraintBuilderDesc = getDescriptor(dataStream.getConstraintBuilderClass());
                            codeBuilder.loadConstant(constraint.getName());
                            codeBuilder.invokeinterface(constraintBuilderDesc, "asConstraint", MethodTypeDesc.of(constraintDesc, stringDesc));

                            codeBuilder.aastore();
                            index++;
                        }
                        codeBuilder.return_(TypeKind.REFERENCE);
                    });
        });
        SolverResource.GENERATED_CLASS_LOADER.get().addClass(constraintProviderClassName, classBytes);
        var out = SolverResource.GENERATED_CLASS_LOADER.get().getClassForDomainClassName(constraintProviderClassName);
        for (var initializer : classInitializerList) {
            initializer.accept(out);
        }
        classInitializerList.clear();
        return (Class<? extends ConstraintProvider>) out;
    }

    private enum FunctionType {
        PREDICATE(WasmFunction::asPredicate),
        MAPPER(WasmFunction::asFunction),
        TO_INT(WasmFunction::asToIntFunction);

        private final TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor;

        FunctionType(TriFunction<WasmFunction, Integer, Instance, Object> functionConvertor) {
            this.functionConvertor = functionConvertor;
        }

        public ClassDesc getClassDescriptor(DataStream dataStream, int extras) {
            return getDescriptor(switch (this) {
                case PREDICATE -> dataStream.getPredicateClassWithExtras(extras);
                case MAPPER -> dataStream.getFunctionClassWithExtras(extras);
                case TO_INT -> dataStream.getToIntFunctionClassWithExtras(extras);
            });
        }

        public Object getFunction(int size, WasmFunction wasmFunction) {
            return functionConvertor.apply(wasmFunction, size, SolverResource.INSTANCE.get());
        }
    }

    record DataStreamInfo(ClassBuilder classBuilder,
                          CodeBuilder codeBuilder,
                          DataStream dataStream,
                          ClassDesc generatedClassDesc) {}

    private ClassDesc loadFunction(DataStreamInfo dataStreamInfo, FunctionType functionType,
            WasmFunction function) {
        return loadFunction(dataStreamInfo, 0, functionType, function);
    }

    private ClassDesc loadFunction(DataStreamInfo dataStreamInfo, int extras, FunctionType functionType,
            WasmFunction function) {
        var argCount = dataStreamInfo.dataStream.getTupleSize() + extras;
        var functionInstance = functionType.getFunction(argCount, function);
        var functionFieldName = "$function" + functionCount;
        var functionClassDesc = functionType.getClassDescriptor(dataStreamInfo.dataStream, extras);
        functionCount++;
        dataStreamInfo.classBuilder.withField(functionFieldName, functionClassDesc, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
        classInitializerList.add(clazz -> {
            try {
                clazz.getField(functionFieldName).set(null, functionInstance);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Type mismatch: expected %s but got %s".formatted(functionClassDesc, functionInstance.getClass().getInterfaces()[0]), e);
            }
        });
        dataStreamInfo.codeBuilder.getstatic(dataStreamInfo.generatedClassDesc, functionFieldName, functionClassDesc);
        return functionClassDesc;
    }

    private DataStream generateConstraintBody(ClassDesc generatedClass,
            ClassDesc scoreDesc,
            ClassBuilder classBuilder,
            CodeBuilder codeBuilder,
            WasmConstraint wasmConstraint) {
        DataStream dataStream = new DataStream();
        var dataStreamInfo = new DataStreamInfo(classBuilder, codeBuilder, dataStream, generatedClass);
        var classLoader = SolverResource.GENERATED_CLASS_LOADER.get();
        for (var steamComponent : wasmConstraint.getStreamComponentList()) {
            var streamDesc = getDescriptor(dataStream.getConstraintStreamClass());

            switch (steamComponent) {
                case ForEachComponent forEachComponent -> {
                    codeBuilder.aload(1);
                    codeBuilder.loadConstant(getDescriptor(classLoader.getClassForDomainClassName(
                            forEachComponent.className())));
                    codeBuilder.invokeinterface(constraintFactoryDesc, "forEach", MethodTypeDesc.of(streamDesc, getDescriptor(Class.class)));
                }
                case FilterComponent filterComponent -> {
                    var predicateDesc = loadFunction(dataStreamInfo, FunctionType.PREDICATE, filterComponent.filter());
                    codeBuilder.invokeinterface(streamDesc, "filter", MethodTypeDesc.of(streamDesc, predicateDesc));
                }
                case JoinComponent(String className) -> {
                    codeBuilder.loadConstant(getDescriptor(classLoader.getClassForDomainClassName(className)));
                    codeBuilder.invokeinterface(streamDesc, "join", MethodTypeDesc.of(
                            getDescriptor(dataStream.getConstraintStreamClassWithExtras(1)), getDescriptor(Class.class)));
                }
                case PenalizeComponent penalizeComponent -> {
                    codeBuilder.loadConstant(penalizeComponent.weight());
                    codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                    if (penalizeComponent.scaleBy() != null) {
                        var functionDesc = loadFunction(dataStreamInfo, FunctionType.TO_INT, penalizeComponent.scaleBy());
                        codeBuilder.invokeinterface(streamDesc, "penalize", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class), functionDesc));
                    } else {
                        codeBuilder.invokeinterface(streamDesc, "penalize", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class)));
                    }
                }
                case RewardComponent rewardComponent -> {
                    codeBuilder.loadConstant(rewardComponent.weight());
                    codeBuilder.invokestatic(scoreDesc, "parseScore", MethodTypeDesc.of(scoreDesc, stringDesc));
                    if (rewardComponent.scaleBy() != null) {
                        var functionDesc = loadFunction(dataStreamInfo, FunctionType.TO_INT, rewardComponent.scaleBy());
                        codeBuilder.invokeinterface(streamDesc, "reward", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class), functionDesc));
                    } else {
                        codeBuilder.invokeinterface(streamDesc, "reward", MethodTypeDesc.of(
                                getDescriptor(dataStream.getConstraintBuilderClass()), getDescriptor(Score.class)));
                    }
                }
            }

            steamComponent.applyToDataStream(dataStream);
        }
        return dataStream;
    }
}
