package traben.entity_model_features.animation;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.models.animation.math.asm.ASMVariableHandler;
import traben.entity_model_features.models.animation.math.expression_tree.MathComponent;
import traben.entity_model_features.models.animation.math.expression_tree.MathMethod;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Uses the API's supplied ASM path, with the interpreter as the upstream-compatible fallback. */
public final class CompiledExtension {

    public interface Evaluator { float evaluate(float[] floats, boolean[] bools); }
    private static final AtomicInteger NEXT = new AtomicInteger();
    private record BindingContext(String className, List<MathComponent> arguments) { }
    private static final ThreadLocal<BindingContext> BINDINGS = new ThreadLocal<>();

    /** Evaluate argument expressions at their bytecode use site, preserving conditional laziness. */
    public static void visitArgument(MathComponent component, MethodVisitor visitor, ASMVariableHandler variables) {
        BindingContext context = BINDINGS.get();
        int index = context.arguments.size();
        context.arguments.add(component);
        visitor.visitFieldInsn(Opcodes.GETSTATIC, context.className, "arguments",
                "[Ltraben/entity_model_features/models/animation/math/expression_tree/MathComponent;");
        visitor.visitLdcInsn(index);
        visitor.visitInsn(Opcodes.AALOAD);
        visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "traben/entity_model_features/models/animation/math/expression_tree/MathComponent", "getResult", "()F", true);
        if (variables.isScopeBool()) visitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                "traben/entity_model_features/models/animation/math/expression_tree/MathValue", "toBoolean", "(F)Z", false);
    }

    public static Supplier<Float> compile(MathMethod method) {
        if (method.isConstant() || !method.hasKnownResultType()) return method::getResult;
        String name = "traben/entity_model_features/animation/GeneratedFunction" + NEXT.incrementAndGet();
        BindingContext bindings = new BindingContext(name, new ArrayList<>());
        BindingContext previous = BINDINGS.get();
        BINDINGS.set(bindings);
        try {
            ASMVariableHandler variables = new ASMVariableHandler();
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
            writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "arguments",
                    "[Ltraben/entity_model_features/models/animation/math/expression_tree/MathComponent;", null, null).visitEnd();
            var visitor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "evaluate", "([F[Z)F", null, null);
            visitor.visitCode();
            variables.scope(method.returnsBoolean());
            method.asmVisit(visitor, variables);
            variables.scopePop();
            if (method.returnsBoolean()) visitor.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "traben/entity_model_features/models/animation/math/expression_tree/MathValue", "fromBoolean", "(Z)F", false);
            visitor.visitInsn(Opcodes.FRETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
            variables.verifyEndOfParse();
            writer.visitEnd();
            byte[] bytecode = writer.toByteArray();
            if (EMFConfig.get().logASM) {
                System.out.println("[EMF] ASM animation " + name
                        + " bytes=" + bytecode.length
                        + " floatVars=" + variables.getFloatVarList()
                        + " boolVars=" + variables.getBoolVarList()
                        + " arguments=" + bindings.arguments.size());
            }
            Class<?> type = new ClassLoader(CompiledExtension.class.getClassLoader()) {
                Class<?> define() { return defineClass(name.replace('/', '.'), bytecode, 0, bytecode.length); }
            }.define();
            type.getField("arguments").set(null, bindings.arguments.toArray(MathComponent[]::new));
            var handle = java.lang.invoke.MethodHandles.publicLookup().findStatic(type, "evaluate",
                    java.lang.invoke.MethodType.methodType(float.class, float[].class, boolean[].class));
            return () -> {
                float[] floats = new float[variables.getFloatVarList().size()];
                boolean[] bools = new boolean[variables.getBoolVarList().size()];
                for (int i = 0; i < floats.length; i++) floats[i] = value(variables.getFloatVarList().get(i));
                for (int i = 0; i < bools.length; i++) {
                    float value = value(variables.getBoolVarList().get(i));
                    bools[i] = MathValue.isBoolean(value) ? MathValue.toBoolean(value) : value != 0 && !Float.isNaN(value);
                }
                try { return (float) handle.invokeExact(floats, bools); }
                catch (Throwable exception) { throw new IllegalStateException("Compiled animation function failed", exception); }
            };
        } catch (Exception | LinkageError exception) {
            System.err.println("[EMF] Custom function ASM fallback: " + exception.getMessage());
            return method::getResult;
        } finally {
            if (previous == null) BINDINGS.remove(); else BINDINGS.set(previous);
        }
    }

    private static float value(String key) {
        return (float) AnimationExtensions.current().get(key);
    }
}
