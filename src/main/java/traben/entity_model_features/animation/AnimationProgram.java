package traben.entity_model_features.animation;
import traben.entity_model_features.models.animation.AnimSetupContext;

public final class AnimationProgram {

    public record Assignment(String target, Expression.Node expression) {
    }

    public static Assignment compile(String target, String expression) {
        return new Assignment(target, Expression.compile(expression));
    }

    public static Assignment compile(String target, String expression, AnimSetupContext context) {
        context.animKey = target;
        return new Assignment(target, Expression.compile(expression, context));
    }

    public static boolean isValidExpression(String expression) {
        return Expression.isValid(expression);
    }
}
