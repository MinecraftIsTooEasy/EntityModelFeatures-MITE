package traben.entity_model_features.models.animation.math.variables.factories;

import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import java.util.function.BooleanSupplier;
import traben.entity_model_features.config.EMFConfig;

public abstract class UniqueVariableFactory {

    public abstract MathValue.ResultSupplier getSupplierOrNull(String key, AnimSetupContext context);
    public abstract boolean createsThisVariable(String key);
    public abstract String getExplanationTranslationKey();
    public abstract String getTitleTranslationKey();
    public BooleanSupplier getASMBoolSupplierOrNull(String key, AnimSetupContext context) {
        var supplier = getSupplierOrNull(key, context);
        return supplier == null ? null : () -> supplier.get() > 0;
    }
    public MathValue.ResultSupplier getASMFloatSupplierOrNull(String key, AnimSetupContext context) {
        return getSupplierOrNull(key, context);
    }
    @Override
    public boolean equals(Object other) { return other != null && other.getClass() == getClass(); }

    @Override
    public int hashCode() { return getClass().hashCode(); }

    protected boolean printing() { return EMFConfig.get().logModelCreationData; }
}
