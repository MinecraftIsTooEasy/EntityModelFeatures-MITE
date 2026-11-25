package traben.entity_model_features.config;

import com.google.gson.reflect.TypeToken;
import traben.entity_texture_features.config.screens.FeatureConfigOption;
import traben.entity_texture_features.config.ETFConfig;
import traben.entity_model_features.config.EMFConfig.CustomEntityModelSupportMode;
import traben.entity_model_features.config.EMFConfig.RenderModeChoice;
import traben.entity_model_features.config.EMFConfig.VanillaModelRenderMode;

import java.util.List;
import java.util.Map;

import static traben.entity_texture_features.config.screens.FeatureConfigOption.choice;
import static traben.entity_texture_features.config.screens.FeatureConfigOption.strings;
import static traben.entity_texture_features.config.screens.FeatureConfigOption.value;
import static traben.entity_texture_features.config.screens.FeatureConfigOption.toggle;
import static traben.entity_texture_features.config.screens.FeatureConfigOption.map;

/** UI bindings retain the original serialized option names. */
public final class EMFConfigOptions {

    public static final List<FeatureConfigOption<EMFConfig, ?>> OPTIONS = List.of(
            choice("allowedCEM", CustomEntityModelSupportMode.values(), (EMFConfig config) -> config.allowedCEM,
                    (config, value) -> config.allowedCEM = value),
            strings("modelsNamesDisabled", (EMFConfig config) -> config.modelsNamesDisabled,
                    (config, value) -> config.modelsNamesDisabled = value),
            choice("modelUpdateFrequency", ETFConfig.UpdateFrequency.values(), (EMFConfig config) -> config.modelUpdateFrequency,
                    (config, value) -> config.modelUpdateFrequency = value),
            value("animationLODDistance", Integer.class, (EMFConfig config) -> config.animationLODDistance,
                    (config, value) -> config.animationLODDistance = value),
            toggle("retainDetailOnLowFps", (EMFConfig config) -> config.retainDetailOnLowFps,
                    (config, value) -> config.retainDetailOnLowFps = value),
            toggle("retainDetailOnLargerMobs", (EMFConfig config) -> config.retainDetailOnLargerMobs,
                    (config, value) -> config.retainDetailOnLargerMobs = value),
            toggle("preventFirstPersonHandAnimating", (EMFConfig config) -> config.preventFirstPersonHandAnimating,
                    (config, value) -> config.preventFirstPersonHandAnimating = value),
            toggle("onlyClientPlayerModel", (EMFConfig config) -> config.onlyClientPlayerModel,
                    (config, value) -> config.onlyClientPlayerModel = value),
            toggle("doubleChestAnimFix", (EMFConfig config) -> config.doubleChestAnimFix,
                    (config, value) -> config.doubleChestAnimFix = value),
            toggle("enforceOptifineVariationRequiresDefaultModel_v2", (EMFConfig config) -> config.enforceOptifineVariationRequiresDefaultModel_v2,
                    (config, value) -> config.enforceOptifineVariationRequiresDefaultModel_v2 = value),
            toggle("resetPlayerModelEachRender_v2", (EMFConfig config) -> config.resetPlayerModelEachRender_v2,
                    (config, value) -> config.resetPlayerModelEachRender_v2 = value),
            toggle("enforceOptifineSubFoldersVariantOnly", (EMFConfig config) -> config.enforceOptifineSubFoldersVariantOnly,
                    (config, value) -> config.enforceOptifineSubFoldersVariantOnly = value),
            toggle("enforceOptiFineAnimSyntaxLimits", (EMFConfig config) -> config.enforceOptiFineAnimSyntaxLimits,
                    (config, value) -> config.enforceOptiFineAnimSyntaxLimits = value),
            toggle("allowOptifineFallbackProperties", (EMFConfig config) -> config.allowOptifineFallbackProperties,
                    (config, value) -> config.allowOptifineFallbackProperties = value),
            toggle("enforceOptiFineFloorUVs", (EMFConfig config) -> config.enforceOptiFineFloorUVs,
                    (config, value) -> config.enforceOptiFineFloorUVs = value),
            toggle("asmMaths", (EMFConfig config) -> config.asmMaths,
                    (config, value) -> config.asmMaths = value),
            toggle("logASM", (EMFConfig config) -> config.logASM,
                    (config, value) -> config.logASM = value),
            toggle("logModelCreationData", (EMFConfig config) -> config.logModelCreationData,
                    (config, value) -> config.logModelCreationData = value),
            toggle("debugOnRightClick", (EMFConfig config) -> config.debugOnRightClick,
                    (config, value) -> config.debugOnRightClick = value),
            toggle("automaticModelExporting", (EMFConfig config) -> config.automaticModelExporting,
                    (config, value) -> config.automaticModelExporting = value),
            toggle("exportRotations", (EMFConfig config) -> config.exportRotations,
                    (config, value) -> config.exportRotations = value),
            toggle("showReloadErrorToast", (EMFConfig config) -> config.showReloadErrorToast,
                    (config, value) -> config.showReloadErrorToast = value),
            toggle("onlyDebugRenderOnHover", (EMFConfig config) -> config.onlyDebugRenderOnHover,
                    (config, value) -> config.onlyDebugRenderOnHover = value),
            choice("renderModeChoice", RenderModeChoice.values(), (EMFConfig config) -> config.renderModeChoice,
                    (config, value) -> config.renderModeChoice = value),
            choice("vanillaModelHologramRenderMode_2", VanillaModelRenderMode.values(), (EMFConfig config) -> config.vanillaModelHologramRenderMode_2,
                    (config, value) -> config.vanillaModelHologramRenderMode_2 = value),
            map("entityRenderModeOverrides", new TypeToken<Map<String, RenderModeChoice>>() { }, (EMFConfig config) -> config.entityRenderModeOverrides,
                    (config, value) -> config.entityRenderModeOverrides = value),
            map("entityVanillaHologramOverrides", new TypeToken<Map<String, VanillaModelRenderMode>>() { }, (EMFConfig config) -> config.entityVanillaHologramOverrides,
                    (config, value) -> config.entityVanillaHologramOverrides = value)
    );
}
