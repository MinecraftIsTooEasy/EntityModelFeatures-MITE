package traben.entity_model_features.models.animation.math.methods;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.EMFMathException;
import traben.entity_model_features.models.animation.math.asm.ASMVariableHandler;
import traben.entity_model_features.models.animation.math.asm.ASMVisitable;
import traben.entity_model_features.models.animation.math.expression_tree.MathMethod;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MethodRegistry {

    public interface MethodFactory {
        MathMethod getMethod(List<String> args, boolean negative, AnimSetupContext context) throws EMFMathException;
    }
    private static final MethodRegistry INSTANCE = new MethodRegistry();
    private final Map<String, MethodFactory> factories = new LinkedHashMap<>();
    private final Map<String, String> explanations = new LinkedHashMap<>();
    public static MethodRegistry getInstance() { return INSTANCE; }
    public MethodFactory getMethodFactory(String key) { return factories.get(key); }
    public boolean containsMethod(String key) { return factories.containsKey(key); }
    public Map<String, String> getMethodExplanationTranslationKeys() { return Map.copyOf(explanations); }
    public void registerAndWrapMethodFactory(String key, String explanation, MethodFactory factory) {
        factories.put(key, factory); explanations.put(key, explanation);
    }
    public void registerSimpleMethodFactory(String key, String explanation, Method method, ASMVisitable compiler) {
        if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != float.class && method.getReturnType() != boolean.class)
            throw new IllegalArgumentException("Function must be public static and return float or boolean");
        for (Class<?> type : method.getParameterTypes()) if (type != float.class && type != boolean.class && type != String.class)
            throw new IllegalArgumentException("Unsupported function parameter " + type);
        // Method parameter types must be available before MathMethod parses arguments.
        factories.put(key, (args, negative, context) -> {
            Class<?>[] previous = PARAMETER_TYPES.get();
            PARAMETER_TYPES.set(method.getParameterTypes());
            try { return new StaticMethod(args, negative, context, method, compiler); }
            finally {
                if (previous == null) PARAMETER_TYPES.remove(); else PARAMETER_TYPES.set(previous);
            }
        });
        explanations.put(key, explanation);
    }

    private static final ThreadLocal<Class<?>[]> PARAMETER_TYPES = new ThreadLocal<>();
    private static final class StaticMethod extends MathMethod {

        private final Method method;
        private final ASMVisitable compiler;
        StaticMethod(List<String> args, boolean negative, AnimSetupContext context, Method method, ASMVisitable compiler)
                throws EMFMathException {
            super(negative, context, args, method.getParameterCount());
            this.method = method; this.compiler = compiler;
            setSupplierAndOptimize(() -> {
                Object[] inputs = new Object[rawArgs.size()];
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < inputs.length; i++) {
                    if (types[i] == String.class) inputs[i] = rawArgs.get(i);
                    else if (types[i] == boolean.class) inputs[i] = MathValue.toBoolean(parsedArgs.get(i).getResult());
                    else inputs[i] = parsedArgs.get(i).getResult();
                }
                try {
                    Object result = method.invoke(null, inputs);
                    return returnsBoolean() ? MathValue.fromBoolean((boolean) result) : (float) result;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Animation extension failed", exception);
                }
            }, parsedArgs);
        }

        @Override
        protected boolean isRawStringArg(int index) { return PARAMETER_TYPES.get()[index] == String.class; }

        @Override
        protected boolean hasCorrectArgCount(int count) { return true; }

        @Override
        public boolean returnsBoolean() { return method.getReturnType() == boolean.class; }

        @Override
        public boolean hasKnownResultType() { return true; }

        @Override
        public void asmVisitInner(MethodVisitor visitor, ASMVariableHandler variables) throws EMFMathException {
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < types.length; i++) {
                if (types[i] == String.class) visitor.visitLdcInsn(rawArgs.get(i));
                else {
                    variables.scope(types[i] == boolean.class);
                    parsedArgs.get(i).asmVisit(visitor, variables);
                    variables.scopePop();
                }
            }
            if (compiler != null) compiler.asmVisit(visitor, variables);
            else visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(method.getDeclaringClass()),
                    method.getName(), Type.getMethodDescriptor(method), method.getDeclaringClass().isInterface());
        }
    }
}
