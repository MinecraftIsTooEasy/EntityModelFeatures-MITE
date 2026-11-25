package traben.entity_model_features.config;

import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.Minecraft;
import traben.entity_texture_features.config.ETFConfig;
import traben.entity_texture_features.config.JsonConfigFile;
import traben.entity_texture_features.features.ETFRenderContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** EMF 3.2.4 settings with the same serialized keys for features present in MITE. */
public final class EMFConfig {

    public static final JsonConfigFile<EMFConfig> FILE = new JsonConfigFile<>(EMFConfig.class, EMFConfig::new);
    public CustomEntityModelSupportMode allowedCEM = CustomEntityModelSupportMode.ALL;
    public Set<String> modelsNamesDisabled = new HashSet<>();
    public ETFConfig.UpdateFrequency modelUpdateFrequency = ETFConfig.UpdateFrequency.Average;
    public int animationLODDistance = 20;
    public boolean retainDetailOnLowFps = true;
    public boolean retainDetailOnLargerMobs = true;
    public boolean preventFirstPersonHandAnimating = false;
    public boolean onlyClientPlayerModel = false;
    public boolean doubleChestAnimFix = true;
    public boolean enforceOptifineVariationRequiresDefaultModel_v2 = false;
    public boolean resetPlayerModelEachRender_v2 = true;
    public boolean enforceOptifineSubFoldersVariantOnly = false;
    public boolean enforceOptiFineAnimSyntaxLimits = true;
    public boolean allowOptifineFallbackProperties = true;
    public boolean enforceOptiFineFloorUVs = true;
    public boolean asmMaths = true;
    public boolean logASM = false;
    public boolean logModelCreationData = false;
    public boolean debugOnRightClick = false;
    public boolean automaticModelExporting = false;
    public boolean exportRotations = false;
    public boolean showReloadErrorToast = true;
    public boolean onlyDebugRenderOnHover = false;
    public RenderModeChoice renderModeChoice = RenderModeChoice.NORMAL;
    public VanillaModelRenderMode vanillaModelHologramRenderMode_2 = VanillaModelRenderMode.OFF;
    public Map<String, RenderModeChoice> entityRenderModeOverrides = new HashMap<>();
    public Map<String, VanillaModelRenderMode> entityVanillaHologramOverrides = new HashMap<>();

    public static EMFConfig get() { return FILE.get(); }

    public boolean isModelDisabled(String name) { return modelsNamesDisabled.contains(name); }

    public boolean allows(String name, boolean known, Entity entity) {
        return !isModelDisabled(name) && (known ? allowedCEM.allowsKnown() : allowedCEM.allowsUnknowns())
                && !(onlyClientPlayerModel && entity instanceof EntityPlayer && entity != Minecraft.getMinecraft().thePlayer);
    }

    public RenderModeChoice getRenderModeFor(Entity entity) {
        if (onlyDebugRenderOnHover && !hovered(entity)) return RenderModeChoice.NORMAL;
        return entityRenderModeOverrides.getOrDefault(ETFConfig.entityKey(entity), renderModeChoice);
    }

    public VanillaModelRenderMode getVanillaHologramModeFor(Entity entity) {
        if (onlyDebugRenderOnHover && !hovered(entity)) return VanillaModelRenderMode.OFF;
        return entityVanillaHologramOverrides.getOrDefault(ETFConfig.entityKey(entity), vanillaModelHologramRenderMode_2);
    }

    private static boolean hovered(Entity entity) {
        Minecraft client = Minecraft.getMinecraft();
        return client != null && client.objectMouseOver != null && client.objectMouseOver.getEntityHit() == entity;
    }

    public boolean pauseFirstPerson() { return preventFirstPersonHandAnimating && ETFRenderContext.isFirstPersonHand(); }

    public enum CustomEntityModelSupportMode {
        OFF, ALL, KNOWN_ONLY, UNKNOWN_ONLY;
        public boolean allowsKnown() { return this == ALL || this == KNOWN_ONLY; }
        public boolean allowsUnknowns() { return this == ALL || this == UNKNOWN_ONLY; }
        public boolean isOn() { return this != OFF; }
        public boolean allowsAll() { return this == ALL; }
    }

    public enum RenderModeChoice { NORMAL, GREEN, LINES_AND_TEXTURE, LINES_AND_TEXTURE_FLASH, LINES, NONE }
    public enum VanillaModelRenderMode { OFF, NORMAL, OFFSET }
}
