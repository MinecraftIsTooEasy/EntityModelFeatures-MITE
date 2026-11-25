package traben.entity_model_features.animation;

import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.models.animation.math.EMFMathException;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import traben.entity_model_features.models.animation.math.methods.MethodRegistry;
import traben.entity_model_features.models.animation.math.variables.factories.UniqueVariableFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Extensible compiler and nested evaluation context for the public animation API. */
public final class AnimationExtensions {

    public static final Map<String, Supplier<Float>> VARIABLES = new LinkedHashMap<>();
    public static final Map<String, String> EXPLANATIONS = new LinkedHashMap<>();
    public static final List<String> BOOLEAN_VARIABLES = new ArrayList<>();
    public static final List<UniqueVariableFactory> UNIQUE_VARIABLES = new ArrayList<>();
    private static final ThreadLocal<RenderParameters> CURRENT = new ThreadLocal<>();

    public static RenderParameters current() { return CURRENT.get(); }

    public static double evaluate(RenderParameters parameters, Supplier<Float> supplier) {
        return evaluate(parameters, supplier, Expression.ValueType.DYNAMIC);
    }

    public static double evaluate(RenderParameters parameters, Supplier<Float> supplier, Expression.ValueType type) {
        float value = evaluateRaw(parameters, supplier);
        return type == Expression.ValueType.FLOAT ? value : normalize(value);
    }

    public static float evaluateRaw(RenderParameters parameters, Supplier<Float> supplier) {
        RenderParameters previous = CURRENT.get();
        CURRENT.set(parameters);
        try { return supplier.get(); }
        finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    private static Expression.Node extension(Supplier<Float> supplier, Expression.ValueType type, boolean constant) {
        return new Expression.Node() {
            public double evaluate(RenderParameters parameters) { return AnimationExtensions.evaluate(parameters, supplier, type); }
            public float evaluateApi(RenderParameters parameters) { return evaluateRaw(parameters, supplier); }
            public Expression.ValueType type() { return type; }
            public boolean isConstant() { return constant; }
        };
    }

    public static double normalize(float value) {
        if (value == MathValue.FALSE) return 0;
        if (value == MathValue.TRUE) return 1;
        return value;
    }

    public static Expression.Node variable(String key, AnimSetupContext context) {
        Supplier<Float> singleton = VARIABLES.get(key);
        if (singleton != null) {
            Expression.ValueType type = BOOLEAN_VARIABLES.contains(key) ? Expression.ValueType.BOOLEAN : Expression.ValueType.FLOAT;
            return extension(singleton, type, false);
        }
        for (UniqueVariableFactory factory : UNIQUE_VARIABLES) {
            if (!factory.createsThisVariable(key)) continue;
            MathValue.ResultSupplier supplier = factory.getSupplierOrNull(key, context);
            if (supplier != null) return extension(supplier::get, Expression.ValueType.DYNAMIC, false);
        }
        return null;
    }

    public static Expression.Node function(String name, List<String> raw, AnimSetupContext context) {
        MethodRegistry.MethodFactory factory = MethodRegistry.getInstance().getMethodFactory(name);
        if (factory == null) return null;
        try {
            var method = factory.getMethod(raw, false, context);
            Supplier<Float> compiled = EMFConfig.get().asmMaths ? CompiledExtension.compile(method) : method::getResult;
            Expression.ValueType type = method.hasKnownResultType()
                    ? method.returnsBoolean() ? Expression.ValueType.BOOLEAN : Expression.ValueType.FLOAT
                    : Expression.ValueType.DYNAMIC;
            return extension(compiled, type, method.isConstant());
        } catch (EMFMathException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

}
