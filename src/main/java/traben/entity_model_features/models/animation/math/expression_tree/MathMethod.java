package traben.entity_model_features.models.animation.math.expression_tree;

import org.objectweb.asm.MethodVisitor;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.EMFMathException;
import traben.entity_model_features.models.animation.math.asm.ASMVariableHandler;
import java.util.ArrayList;
import java.util.List;

public abstract class MathMethod extends MathValue {

    protected MathComponent optimizedAlternativeToThis;
    protected ResultSupplier supplier;
    protected final List<String> rawArgs;
    protected final List<MathComponent> parsedArgs = new ArrayList<>();
    protected boolean isInvertedBoolean;

    protected MathMethod(boolean negative, AnimSetupContext context, List<String> args) throws EMFMathException {
        this(negative, context, args, -1);
    }

    protected MathMethod(boolean negative, AnimSetupContext context, List<String> args, int count) throws EMFMathException {
        super(negative);
        rawArgs = List.copyOf(args);
        if (count < 0 ? !hasCorrectArgCount(args.size()) : count != args.size())
            throw new EMFMathException("Wrong number of arguments in " + context.modelName + ": " + context.animKey);
        for (int i = 0; i < args.size(); i++) parsedArgs.add(isRawStringArg(i) ? null
                : MathExpressionParser.getOptimizedExpression(args.get(i), false, context));
    }

    protected abstract boolean hasCorrectArgCount(int count);
    protected boolean isRawStringArg(int index) { return false; }
    protected boolean canOptimizeForConstantArgs() { return true; }
    public boolean returnsBoolean() { return false; }
    /** API 9 custom factories need not declare a result type; those use the interpreter. */
    public boolean hasKnownResultType() { return false; }
    @Override
    public boolean isConstant() { return optimizedAlternativeToThis != null && optimizedAlternativeToThis.isConstant(); }
    protected void setOptimizedAlternativeToThis(MathComponent component) { optimizedAlternativeToThis = component; }
    protected void setSupplierAndOptimize(ResultSupplier supplier) { this.supplier = supplier; }
    protected void setSupplierAndOptimize(ResultSupplier supplier, MathComponent arg) {
        setSupplierAndOptimize(supplier, List.of(arg));
    }
    protected void setSupplierAndOptimize(ResultSupplier supplier, List<MathComponent> args) {
        this.supplier = supplier;
        setOptimizedIfPossible(supplier, args);
    }
    protected void setOptimizedIfPossible(ResultSupplier supplier, List<MathComponent> args) {
        if (canOptimizeForConstantArgs() && !args.isEmpty()
                && args.stream().allMatch(arg -> arg == null || arg.isConstant())) {
            float value = supplier.get();
            if (!Float.isNaN(value)) optimizedAlternativeToThis = new MathConstant(value, isNegative);
        }
    }

    @Override
    ResultSupplier getResultSupplier() { return supplier; }

    @Override
    public float getResult() {
        return optimizedAlternativeToThis == null ? super.getResult() : optimizedAlternativeToThis.getResult();
    }

    @Override
    public final void asmVisit(MethodVisitor visitor, ASMVariableHandler variables) throws EMFMathException {
        asmVisitInner(visitor, variables);
        if (isInvertedBoolean) variables.asmInvertBoolean(visitor);
        if (isNegative) variables.asmNegateFloat(visitor);
    }

    public abstract void asmVisitInner(MethodVisitor visitor, ASMVariableHandler variables) throws EMFMathException;
}
