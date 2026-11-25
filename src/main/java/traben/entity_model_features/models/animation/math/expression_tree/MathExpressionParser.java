package traben.entity_model_features.models.animation.math.expression_tree;

import org.objectweb.asm.MethodVisitor;
import traben.entity_model_features.animation.AnimationExtensions;
import traben.entity_model_features.animation.Expression;
import traben.entity_model_features.animation.CompiledExtension;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.EMFMathException;
import traben.entity_model_features.models.animation.math.asm.ASMVariableHandler;

public final class MathExpressionParser {

    public static final MathComponent NULL_EXPRESSION = new MathConstant(Float.NaN);

    public static MathComponent getOptimizedExpression(String source, boolean negative, AnimSetupContext context)
            throws EMFMathException {
        final Expression.Node node;
        try { node = Expression.compileStrict(source, context); }
        catch (RuntimeException exception) { throw new EMFMathException(exception.getMessage()); }
        if (node.isConstant()) {
            return new MathConstant(node.evaluateApi(null), negative);
        }
        return new MathComponent() {
            @Override
            public float getResult() {
                float value = node.evaluateApi(AnimationExtensions.current());
                return negative ? -value : value;
            }

            @Override
            public void asmVisit(MethodVisitor visitor, ASMVariableHandler variables) {
                CompiledExtension.visitArgument(this, visitor, variables);
            }
        };
    }
}
