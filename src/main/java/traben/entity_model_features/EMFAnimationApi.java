package traben.entity_model_features;

import net.minecraft.Entity;
import net.minecraft.TileEntity;
import net.minecraft.ModelBase;
import net.minecraft.ModelRenderer;
import traben.entity_model_features.animation.AnimationControl;
import traben.entity_model_features.animation.AnimationExtensions;
import traben.entity_model_features.model.CustomModel;
import traben.entity_model_features.model.CustomPart;
import traben.entity_model_features.model.RenderContext;
import traben.entity_model_features.models.animation.math.asm.ASMVisitable;
import traben.entity_model_features.models.animation.math.expression_tree.MathValue;
import traben.entity_model_features.models.animation.math.methods.MethodRegistry;
import traben.entity_model_features.models.animation.math.variables.factories.UniqueVariableFactory;
import traben.entity_model_features.utils.EMFEntity;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiFunction;

/** API 9 with ModelBase/ModelRenderer/TileEntity in place of the modern Minecraft types. */
public interface EMFAnimationApi {

    static int getApiVersion() { return 9; }
    static EMFEntity getCurrentEntity() { return EMFEntity.of(RenderContext.currentEntity()); }
    static EMFEntity emfEntityOf(Entity entity) { return EMFEntity.of(entity); }
    static EMFEntity emfEntityOf(TileEntity tile) { return EMFEntity.of(tile); }

    static void registerSingletonAnimationVariable(String mod, String name, String explanation, BooleanSupplier supplier) {
        validate(mod, name, explanation, supplier);
        AnimationExtensions.VARIABLES.put(name, () -> MathValue.fromBoolean(supplier));
        AnimationExtensions.BOOLEAN_VARIABLES.add(name);
        AnimationExtensions.EXPLANATIONS.put(name, explanation);
    }
    static void registerSingletonAnimationVariable(String mod, String name, String explanation, Supplier<Float> supplier) {
        validate(mod, name, explanation, supplier);
        AnimationExtensions.VARIABLES.put(name, supplier);
        AnimationExtensions.BOOLEAN_VARIABLES.remove(name);
        AnimationExtensions.EXPLANATIONS.put(name, explanation);
    }
    @Deprecated
    static void registerSingletonAnimationVariable(String mod, String name, BooleanSupplier supplier) {
        registerSingletonAnimationVariable(mod, name, name, supplier);
    }
    @Deprecated
    static void registerSingletonAnimationVariable(String mod, String name, Supplier<Float> supplier) {
        registerSingletonAnimationVariable(mod, name, name, supplier);
    }
    static void registerUniqueAnimationVariableFactory(String mod, String name, UniqueVariableFactory factory) {
        validate(mod, name, factory);
        if (!AnimationExtensions.UNIQUE_VARIABLES.contains(factory)) AnimationExtensions.UNIQUE_VARIABLES.add(factory);
    }
    static void registerCustomFunctionFromStaticMethod(String mod, String name, String explanation,
                                                       Method method, ASMVisitable compiler) {
        validate(mod, name, explanation, method);
        MethodRegistry.getInstance().registerSimpleMethodFactory(name, explanation, method, compiler);
    }
    static void registerCustomFunctionFactory(String mod, String name, String explanation, MethodRegistry.MethodFactory factory) {
        validate(mod, name, explanation, factory);
        MethodRegistry.getInstance().registerAndWrapMethodFactory(name, explanation, factory);
    }
    static boolean registerPauseCondition(Function<EMFEntity, Boolean> listener) {
        validate(listener); AnimationControl.PAUSE_LISTENERS.add(listener); return true;
    }
    static boolean registerVanillaModelCondition(Function<EMFEntity, Boolean> listener) {
        validate(listener); AnimationControl.VANILLA_LISTENERS.add(listener); return true;
    }
    static boolean pauseAllCustomAnimationsForEntity(EMFEntity entity) {
        if (!valid(entity)) return false;
        AnimationControl.PAUSED.add(entity.etf$getUuid()); return true;
    }
    static boolean resumeAllCustomAnimationsForEntity(EMFEntity entity) {
        if (!valid(entity)) return false;
        AnimationControl.PAUSED.remove(entity.etf$getUuid());
        AnimationControl.PAUSED_PARTS.remove(entity.etf$getUuid()); return true;
    }
    static boolean pauseCustomAnimationsForThesePartsOfEntity(EMFEntity entity, ModelRenderer... parts) {
        return pauseParts(entity, parts);
    }
    /** MITE custom geometry is rendered by CustomPart rather than ModelRenderer. */
    static boolean pauseCustomPartsOfEntity(EMFEntity entity, CustomPart... parts) { return pauseParts(entity, parts); }
    private static boolean pauseParts(EMFEntity entity, Object[] parts) {
        if (!valid(entity) || parts == null || parts.length == 0) return false;
        AnimationControl.PAUSED_PARTS.put(entity.etf$getUuid(), parts.clone()); return true;
    }
    static boolean lockEntityToVanillaModel(EMFEntity entity) {
        if (!valid(entity)) return false;
        AnimationControl.VANILLA.add(entity.etf$getUuid()); return true;
    }
    static boolean unlockEntityToVanillaModel(EMFEntity entity) {
        if (!valid(entity)) return false;
        AnimationControl.VANILLA.remove(entity.etf$getUuid()); return true;
    }
    static int getCurrentEMFVariantOfModel(ModelBase model) {
        CustomModel custom = EMFManager.INSTANCE.selectedModel(model);
        return custom == null ? -1 : custom.variant();
    }
    static boolean isModelCustomizedByEMF(ModelBase model) { return EMFManager.INSTANCE.selectedModel(model) != null; }
    static boolean isModelAnimatedByEMF(ModelBase model) {
        CustomModel custom = EMFManager.INSTANCE.selectedModel(model);
        return custom != null && custom.isAnimated();
    }
    static boolean isModelPartCustomToEMF(Object part) { return part instanceof CustomPart; }
    static boolean isModelPartAnimatedByEMF(Object part) {
        CustomModel custom = RenderContext.currentModel();
        return custom != null && custom.isPartAnimated(part);
    }
    @Deprecated
    static void registerAnimationFunction(String mod, String name, String explanation, Function<Float, Float> function) {
        throw deprecated();
    }
    @Deprecated
    static void registerAnimationFunction(String mod, String name, Function<Float, Float> function) { throw deprecated(); }
    @Deprecated
    static void registerAnimationBiFunction(String mod, String name, String explanation, BiFunction<Float, Float, Float> function) {
        throw deprecated();
    }
    @Deprecated
    static void registerAnimationBiFunction(String mod, String name, BiFunction<Float, Float, Float> function) { throw deprecated(); }
    @Deprecated
    static void registerAnimationMultiFunction(String mod, String name, String explanation, Function<List<Float>, Float> function) {
        throw deprecated();
    }
    @Deprecated
    static void registerAnimationMultiFunction(String mod, String name, Function<List<Float>, Float> function) { throw deprecated(); }
    @Deprecated
    static void registerCustomFunctionFactory(String mod, String name, MethodRegistry.MethodFactory factory) { throw deprecated(); }
    private static UnsupportedOperationException deprecated() {
        return new UnsupportedOperationException("EMF Animation API: method is deprecated in API 9");
    }
    private static boolean valid(EMFEntity entity) { return entity != null && entity.etf$getUuid() != null; }
    private static void validate(Object... values) {
        for (Object value : values) if (value == null) throw new InvalidParameterException("EMF Animation API: null registration parameter");
    }
}
