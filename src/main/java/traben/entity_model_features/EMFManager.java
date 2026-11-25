package traben.entity_model_features;

import com.google.gson.Gson;
import net.minecraft.Entity;
import net.minecraft.EntityAgeable;
import net.minecraft.EntityHorse;
import net.minecraft.EntityLiving;
import net.minecraft.EntityLivingBase;
import net.minecraft.EntityOcelot;
import net.minecraft.EntityPigZombie;
import net.minecraft.EntityPlayer;
import net.minecraft.EntitySkeleton;
import net.minecraft.EntityZombie;
import net.minecraft.Minecraft;
import net.minecraft.ModelBase;
import net.minecraft.ResourceLocation;
import net.minecraft.ResourceManager;
import net.minecraft.ResourceManagerReloadListener;
import traben.entity_model_features.model.CustomModel;
import traben.entity_model_features.model.JemModel;
import traben.entity_model_features.model.JemPart;
import traben.entity_model_features.model.DoubleChestBridge;
import traben.entity_model_features.model.ModelResources;
import traben.entity_model_features.model.ModelExporter;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_model_features.animation.AnimationControl;
import traben.entity_model_features.animation.AnimationLOD;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_texture_features.features.player.ModernPlayerModel;
import moddedmite.rustedironcore.api.resource.LegacyTextureResources;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

public final class EMFManager implements ResourceManagerReloadListener {

    public static final EMFManager INSTANCE = new EMFManager();

    private record ModelKey(ModelBase vanillaModel, ResourceLocation resource, String name) {
    }

    private record ModelSelection(ResourceLocation resource, int tick, int ruleIndex) { }

    private record TextureContractKey(ResourceLocation texture, int width, int height) { }

    private static final Gson GSON = new Gson();
    private final Map<ModelKey, CustomModel> models = new HashMap<>();
    private final ModelResources modelResources = new ModelResources();
    private final Set<ModelKey> missingModels = new HashSet<>();
    private final Map<ResourceLocation, String> loadingErrors = new LinkedHashMap<>();
    private final Map<Entity, Map<String, Double>> entityVariables = new WeakHashMap<>();
    private final Map<Entity, Map<String, Double>> guiEntityVariables = new WeakHashMap<>();
    private final Map<Entity, Map<String, Double>> firstPersonEntityVariables = new WeakHashMap<>();
    private final Map<Entity, Map<CustomModel, Map<String, Double>>> previousAnimations = new WeakHashMap<>();
    private final Map<Entity, Map<CustomModel, Map<String, Double>>> guiPreviousAnimations = new WeakHashMap<>();
    private final Map<ModelBase, CustomModel> selectedModels = new WeakHashMap<>();
    private final Map<Entity, Map<String, ModelSelection>> modelSelections = new WeakHashMap<>();
    private final Map<Entity, Float> shadowSizes = new WeakHashMap<>();
    private final Map<Entity, Integer> highestAnger = new WeakHashMap<>();
    private final Map<Entity, Map<String, Double>> renderVariables = new WeakHashMap<>();
    private final Map<String, Double> globalVariables = new HashMap<>();
    private final Map<TextureContractKey, ResourceLocation> textureContracts = new HashMap<>();
    private final Set<TextureContractKey> missingTextureContracts = new HashSet<>();
    private ResourceManager resources;
    private float frameCounter;
    private long frameSequence;
    private double frameTime = 1.0D / 60.0D;
    private long previousFrameNanos;
    private float tickDelta;
    private Entity debugTarget;

    private EMFManager() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        DoubleChestBridge.clear();
        ModelExporter.clear();
        AnimationLOD.clear();
        resources = resourceManager;
        models.clear();
        modelResources.clear();
        missingModels.clear();
        loadingErrors.clear();
        entityVariables.clear();
        guiEntityVariables.clear();
        firstPersonEntityVariables.clear();
        previousAnimations.clear();
        guiPreviousAnimations.clear();
        selectedModels.clear();
        modelSelections.clear();
        globalVariables.clear();
        textureContracts.clear();
        missingTextureContracts.clear();
        shadowSizes.clear();
        highestAnger.clear();
        renderVariables.clear();
        frameCounter = 0.0F;
        frameTime = 1.0D / 60.0D;
        previousFrameNanos = 0L;
        debugTarget = null;
    }

    public CustomModel getModel(Object renderer, ModelBase vanillaModel, Entity entity, boolean renderPass) {
        return getModel(renderer, vanillaModel, entity, renderPass, null);
    }

    public CustomModel getModel(Object renderer, ModelBase vanillaModel, Entity entity,
                                boolean renderPass, String requestedName) {
        if (AnimationControl.shouldUseVanilla(entity)) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        String name = requestedName == null ? modelName(renderer, entity, renderPass) : requestedName;
        // Player skin CEM requires ETF's 64x64 model, but armor layers use
        // independent legacy ModelBiped instances in MITE and have their own
        // player_inner_armor/player_outer_armor CEM contract.
        boolean playerArmorPass = renderPass
                && ("player_inner_armor".equals(name) || "player_outer_armor".equals(name));
        if (entity instanceof EntityPlayer && !(vanillaModel instanceof ModernPlayerModel) && !playerArmorPass) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        if (name == null) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        if (EMFConfig.get().automaticModelExporting) ModelExporter.export(vanillaModel, name);
        boolean known = vanillaModel.getClass().getName().startsWith("net.minecraft.")
                || vanillaModel instanceof traben.entity_model_features.model.ModernHorseModel
                || vanillaModel instanceof traben.entity_model_features.model.ModernChestModel
                || vanillaModel instanceof traben.entity_texture_features.features.player.ModernPlayerModel;
        if (!EMFConfig.get().allows(name, known, entity)) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        ResourceLocation base = selectModel(name, entity);
        if (base == null) {
            selectedModels.remove(vanillaModel);
            if (!renderPass && requestedName == null) {
                shadowSizes.remove(entity);
            }
            return null;
        }
        ModelKey key = new ModelKey(vanillaModel, base, name);
        if (missingModels.contains(key)) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        CustomModel cached = models.get(key);
        if (cached != null) {
            if (!applyTextureContract(cached, base)) {
                selectedModels.remove(vanillaModel);
                return null;
            }
            selectedModels.put(vanillaModel, cached);
            rememberShadowSize(entity, cached, renderPass);
            return cached;
        }
        JemModel data;
        try {
            data = readModel(base);
        } catch (RuntimeException exception) {
            recordLoadingError(base, exception);
            data = null;
        }
        if (data == null) {
            missingModels.add(key);
            selectedModels.remove(vanillaModel);
            return null;
        }
        CustomModel model;
        try {
            model = new CustomModel(data, vanillaModel, name);
        } catch (RuntimeException exception) {
            recordLoadingError(base, exception);
            missingModels.add(key);
            selectedModels.remove(vanillaModel);
            return null;
        }
        if (EMFConfig.get().logModelCreationData) System.out.println("[EMF] Loaded " + base + " for " + name);
        String file = base.getResourcePath();
        var suffix = java.util.regex.Pattern.compile("(\\d+)\\.jem$").matcher(file);
        model.setVariant(suffix.find() ? Integer.parseInt(suffix.group(1)) : 0);
        models.put(key, model);
        if (!applyTextureContract(model, base)) {
            selectedModels.remove(vanillaModel);
            return null;
        }
        selectedModels.put(vanillaModel, model);
        rememberShadowSize(entity, model, renderPass);
        return model;
    }

    public boolean isPlayerArmorPass(Object renderer, Entity entity, boolean renderPass, String requestedName) {
        if (!renderPass || !(entity instanceof EntityPlayer)) return false;
        String name = requestedName == null ? modelName(renderer, entity, true) : requestedName;
        return "player_inner_armor".equals(name) || "player_outer_armor".equals(name);
    }

    /**
     * A CEM model's textureSize is part of its UV contract. Legacy MITE entity
     * and armor renderers can request a texture whose modern equivalent moved
     * to another path and may use a different image size. Bind the actual
     * matching candidate before EMF draws custom geometry; if no candidate
     * satisfies the contract, keep the vanilla model instead of rendering
     * visibly corrupted UVs. This is deliberately in EMF's model layer, not a
     * blind path alias.
     */
    private boolean applyTextureContract(CustomModel model, ResourceLocation modelLocation) {
        ResourceLocation current = ETFRenderContext.currentTexture();
        if (current == null || resources == null) {
            return true;
        }
        String path = current.getResourcePath();
        if (!path.startsWith("textures/entity/") && !path.startsWith("textures/models/armor/")) {
            return true;
        }
        ResourceLocation requested = model.customTexture() == null ? current : model.customTexture();
        TextureContractKey key = new TextureContractKey(requested, model.textureWidth(), model.textureHeight());
        if (missingTextureContracts.contains(key)) {
            return false;
        }
        ResourceLocation selected = textureContracts.get(key);
        if (selected == null && !textureContracts.containsKey(key)) {
            selected = LegacyTextureResources.findMatching(resources, requested,
                    model.textureWidth(), model.textureHeight());
            if (selected == null) {
                missingTextureContracts.add(key);
                if (EMFConfig.get().logModelCreationData) {
                    System.out.println("[EMF] Skipped " + modelLocation + ": texture " + current
                            + " has no " + model.textureWidth() + "x" + model.textureHeight()
                            + " resource candidate");
                }
                return false;
            }
            textureContracts.put(key, selected);
        }
        if (!selected.equals(current)) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(selected);
            ETFRenderContext.setCurrentTexture(selected);
        }
        return true;
    }

    public boolean hasModel(String name, Entity entity) {
        return modelResources.exists(name, fallbackNames(name));
    }

    public static String horseSaddleModelName(EntityHorse horse) {
        return baseEntityName(horse) + "_saddle";
    }

    public boolean hasHorseSaddleModel(EntityHorse horse, ModelBase vanillaModel) {
        return canRenderModel(horseSaddleModelName(horse), vanillaModel, horse);
    }

    /** Check the selected, compiled layer without replacing the body's active model. */
    public boolean canRenderModel(String name, ModelBase vanillaModel, Entity entity) {
        CustomModel previous = selectedModels.get(vanillaModel);
        try {
            return getModel(null, vanillaModel, entity, true, name) != null;
        } finally {
            if (previous == null) selectedModels.remove(vanillaModel);
            else selectedModels.put(vanillaModel, previous);
        }
    }

    public Map<String, Double> getEntityVariables(Entity entity) {
        Map<String, Double> worldVariables = entityVariables.computeIfAbsent(entity, ignored -> new HashMap<>());
        if (ETFRenderContext.isFirstPersonHand()) {
            return firstPersonEntityVariables.computeIfAbsent(entity, ignored -> new HashMap<>(worldVariables));
        }
        if (ETFRenderContext.isInGui()) {
            return guiEntityVariables.computeIfAbsent(entity, ignored -> new HashMap<>(worldVariables));
        }
        return worldVariables;
    }

    public CustomModel selectedModel(ModelBase model) { return selectedModels.get(model); }

    public boolean isModelSelected(Entity entity, String name) {
        ModelSelection selection = modelSelections.getOrDefault(entity, Map.of()).get(name);
        if (selection == null || selection.resource() == null) return false;
        return models.entrySet().stream().anyMatch(entry -> entry.getKey().name().equals(name)
                && entry.getKey().resource().equals(selection.resource())
                && selectedModels.containsValue(entry.getValue()));
    }

    public void requestDebug(Entity entity) {
        debugTarget = entity;
    }

    public boolean beginDebug(Entity entity) {
        // A null render context is used by audits and by a few non-entity paths.
        // It must never match the unset debug target; only an explicit right-click
        // request is allowed to arm the one-shot diagnostic.
        if (debugTarget == null || debugTarget != entity) return false;
        debugTarget = null;
        return true;
    }

    public void printDebug(Entity entity) {
        String entityName = baseEntityName(entity);
        printDebugLine("Entity " + entityName + " [" + entity.getClass().getName() + "]");
        Map<String, ModelSelection> selections = new TreeMap<>(modelSelections.getOrDefault(entity, Map.of()));
        if (selections.isEmpty()) {
            printDebugLine("No custom entity model was selected");
            return;
        }
        selections.forEach((name, selection) -> {
            ResourceLocation resource = selection.resource();
            StringBuilder line = new StringBuilder(name).append(" -> ")
                    .append(resource == null ? "vanilla" : resource)
                    .append(" (rule ").append(selection.ruleIndex()).append(')');
            if (resource != null) {
                models.entrySet().stream()
                        .filter(entry -> entry.getKey().name().equals(name)
                                && entry.getKey().resource().equals(resource))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .ifPresent(model -> line.append(", variant ").append(model.variant())
                                .append(model.isAnimated() ? ", animated" : ", static"));
            }
            printDebugLine(line.toString());
        });
    }

    private static void printDebugLine(String message) {
        String line = "[EMF] " + message;
        System.out.println(line);
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.ingameGUI != null) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(line);
        }
    }

    public Map<ModelBase, CustomModel> captureSelectedModels() { return new IdentityHashMap<>(selectedModels); }

    public void restoreSelectedModels(Map<ModelBase, CustomModel> saved) {
        selectedModels.clear();
        selectedModels.putAll(saved);
    }

    private ResourceLocation selectModel(String name, Entity entity) {
        Map<String, ModelSelection> selections = modelSelections.computeIfAbsent(entity, ignored -> new HashMap<>());
        ModelSelection previous = selections.get(name);
        int tick = entity == null ? 0 : entity.ticksExisted;
        int delay = EMFConfig.get().modelUpdateFrequency.getDelay();
        if (previous != null && (delay < 0 || tick >= previous.tick && tick - previous.tick < delay)) return previous.resource;
        ModelResources.Selection selected = modelResources.selection(name, fallbackNames(name), entity);
        selections.put(name, new ModelSelection(selected.file(), tick, selected.ruleIndex()));
        return selected.file();
    }

    public int modelRuleIndex(Entity entity, String name) {
        Map<String, ModelSelection> selections = modelSelections.get(entity);
        ModelSelection selected = selections == null ? null : selections.get(name);
        return selected == null ? 0 : selected.ruleIndex;
    }

    public Map<String, Double> animationHistory(Entity entity, CustomModel model) {
        return previousAnimations.computeIfAbsent(entity, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(model, ignored -> new LinkedHashMap<>());
    }

    public Map<String, Double> guiAnimationHistory(Entity entity, CustomModel model) {
        return guiPreviousAnimations.computeIfAbsent(entity, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(model, ignored -> new LinkedHashMap<>());
    }

    /** Float API view backed by the exact variable storage used by animations. */
    public Map<String, Float> getFloatVariables(Entity entity) {
        Map<String, Double> backing = getEntityVariables(entity);
        return new java.util.AbstractMap<>() {
            @Override
            public Float put(String key, Float value) {
                Double old = backing.put(key, value.doubleValue());
                return old == null ? null : old.floatValue();
            }

            @Override
            public Set<Entry<String, Float>> entrySet() {
                return new java.util.AbstractSet<>() {
                    public int size() { return backing.size(); }
                    public java.util.Iterator<Entry<String, Float>> iterator() {
                        var iterator = backing.entrySet().iterator();
                        return new java.util.Iterator<>() {
                            public boolean hasNext() { return iterator.hasNext(); }
                            public void remove() { iterator.remove(); }
                            public Entry<String, Float> next() {
                                var entry = iterator.next();
                                return new SimpleEntry<>(entry.getKey(), entry.getValue().floatValue()) {
                                    public Float setValue(Float value) {
                                        Float old = super.setValue(value); entry.setValue(value.doubleValue()); return old;
                                    }
                                };
                            }
                        };
                    }
                };
            }
        };
    }

    public Map<String, Double> getGlobalVariables() {
        return globalVariables;
    }

    public void beginFrame(float tickDelta) {
        frameSequence++;
        this.tickDelta = tickDelta;
        long now = System.nanoTime();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.isGamePaused) {
            frameTime = 1.0E-9D;
        } else if (previousFrameNanos != 0L) {
            frameTime = Math.max(1.0E-9D, Math.min(1.0D, (now - previousFrameNanos) / 1_000_000_000.0D));
        }
        previousFrameNanos = now;
        frameCounter = frameCounter >= 720719.0F ? 0.0F : frameCounter + 1.0F;
    }

    public double getFrameCounter() {
        return frameCounter;
    }

    public long frameSequence() { return frameSequence; }

    public float getTickDelta() {
        return tickDelta;
    }

    public double getFrameTime() {
        return frameTime;
    }

    public float getShadowSize(Entity entity) {
        return shadowSizes.getOrDefault(entity, Float.NaN);
    }

    public void setShadowSize(Entity entity, double value) {
        shadowSizes.put(entity, (float) Math.max(0.0D, value));
    }

    public int observeAnger(Entity entity, int current) {
        if (current <= 0) { highestAnger.remove(entity); return 0; }
        return highestAnger.merge(entity, current, Math::max);
    }

    public double getRenderVariable(Entity entity, String name) {
        double fallback = switch (name) {
            case "render.shadow_opacity", "render.fire_scale", "render.fire_height" -> Double.NaN;
            default -> 0;
        };
        return renderVariables.getOrDefault(entity, Map.of()).getOrDefault(name, fallback);
    }

    public void setRenderVariable(Entity entity, String name, double value) {
        renderVariables.computeIfAbsent(entity, ignored -> new HashMap<>()).put(name, value);
    }

    public double getRenderVariable(Entity entity, String name, double fallback) {
        double value = getRenderVariable(entity, name);
        return Double.isNaN(value) ? fallback : value;
    }

    public void resetRenderVariables(Entity entity) {
        renderVariables.remove(entity);
        shadowSizes.remove(entity);
    }

    public record RenderValues(Map<String, Double> variables, Float shadow) { }

    public RenderValues captureRenderVariables(Entity entity) {
        return new RenderValues(new HashMap<>(renderVariables.getOrDefault(entity, Map.of())), shadowSizes.get(entity));
    }

    public void restoreRenderVariables(Entity entity, RenderValues saved) {
        renderVariables.put(entity, new HashMap<>(saved.variables()));
        if (saved.shadow() == null) shadowSizes.remove(entity);
        else shadowSizes.put(entity, saved.shadow());
    }

    private void rememberShadowSize(Entity entity, CustomModel model, boolean renderPass) {
        if (!renderPass) {
            if (Float.isNaN(model.shadowSize())) {
                shadowSizes.remove(entity);
            } else {
                shadowSizes.put(entity, model.shadowSize());
            }
        }
    }

    private JemModel readModel(ResourceLocation location) {
        JemModel model = readJson(location, JemModel.class);
        if (model == null) {
            return null;
        }
        model.customTexture = resolveTexture(location, model.texture);
        if (model.models == null) model.models = new java.util.ArrayList<>();
        for (JemPart part : model.models) {
            prepareExternalModels(part, location, new HashSet<>());
        }
        Map<String, JemPart> ids = new LinkedHashMap<>();
        collectIds(model.models, ids);
        Set<JemPart> resolving = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (JemPart part : model.models) {
            applyBaseInheritance(part, ids, resolving);
        }
        return model;
    }

    private void prepareExternalModels(JemPart part, ResourceLocation owner, Set<ResourceLocation> loading) {
        normalize(part);
        part.customTexture = resolveTexture(owner, part.texture);
        if (!part.model.isEmpty()) {
            ResourceLocation externalLocation = resolveRelative(owner, part.model, ".jpm");
            if (externalLocation != null && ETFApi.resourceExists(externalLocation) && loading.add(externalLocation)) {
                JemPart external = readJson(externalLocation, JemPart.class);
                if (external != null) {
                    prepareExternalModels(external, externalLocation, loading);
                    part.inheritMissing(external);
                }
                loading.remove(externalLocation);
            }
        }
        if (part.submodel != null) {
            prepareExternalModels(part.submodel, owner, loading);
        }
        for (JemPart child : part.submodels) {
            prepareExternalModels(child, owner, loading);
        }
    }

    private void applyBaseInheritance(JemPart part, Map<String, JemPart> ids, Set<JemPart> resolving) {
        if (!resolving.add(part)) {
            return;
        }
        if (!part.baseId.isEmpty()) {
            JemPart base = ids.get(part.baseId);
            if (base != null && base != part) {
                applyBaseInheritance(base, ids, resolving);
                part.inheritMissing(base);
            }
        }
        if (part.submodel != null) applyBaseInheritance(part.submodel, ids, resolving);
        for (JemPart child : part.submodels) applyBaseInheritance(child, ids, resolving);
        resolving.remove(part);
    }

    private static void collectIds(List<JemPart> parts, Map<String, JemPart> ids) {
        for (JemPart part : parts) {
            if (!part.id.isEmpty()) ids.putIfAbsent(part.id, part);
            if (part.submodel != null) collectIds(List.of(part.submodel), ids);
            collectIds(part.submodels, ids);
        }
    }

    private static void normalize(JemPart part) {
        if (part.texture == null) part.texture = "";
        if (part.invertAxis == null) part.invertAxis = "";
        if (part.mirrorTexture == null) part.mirrorTexture = "";
        if (part.baseId == null) part.baseId = "";
        if (part.model == null) part.model = "";
        if (part.id == null) part.id = "";
        if (part.boxes == null) part.boxes = new java.util.ArrayList<>();
        if (part.sprites == null) part.sprites = new java.util.ArrayList<>();
        if (part.submodels == null) part.submodels = new java.util.ArrayList<>();
        if (part.animations == null) part.animations = new java.util.ArrayList<>();
        if (part.attachments == null) part.attachments = new LinkedHashMap<>();
    }

    private static List<String> fallbackNames(String name) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        collectFallbackNames(name, result);
        return result;
    }

    private static void collectFallbackNames(String name, List<String> result) {
        if (name.endsWith("_baby") || name.contains("_baby_")) {
            String adult = name.replace("_baby", "");
            if (!result.contains(adult)) {
                result.add(adult);
                collectFallbackNames(adult, result);
            }
        }
        if (name.endsWith("_inner_armor")) result.add("inner_armor");
        if (name.endsWith("_outer_armor")) result.add("outer_armor");
        String parent = switch (name) {
            case "chest_left", "chest_right" -> "chest_large";
            case "trapped_chest_left", "trapped_chest_right" -> "trapped_chest_large";
            case "sheep_wool" -> "sheep";
            case "pig_saddle" -> "pig";
            case "wolf_collar" -> "wolf";
            case "creeper_charge" -> "creeper";
            case "slime_outer" -> "slime";
            case "temperate_cow" -> "cow";
            case "temperate_pig" -> "pig";
            case "temperate_chicken" -> "chicken";
            default -> null;
        };
        if (parent != null && !result.contains(parent)) {
            result.add(parent);
            collectFallbackNames(parent, result);
        }
    }

    private String modelName(Object renderer, Entity entity, boolean renderPass) {
        String base = baseEntityName(entity);
        ResourceLocation texture = ETFRenderContext.currentTexture();
        String path = texture == null ? "" : texture.getResourcePath().toLowerCase(Locale.ROOT);
        if (base.equals("cow") || base.equals("pig") || base.equals("chicken")) {
            for (String climate : List.of("cold", "warm", "temperate")) {
                if (path.matches(".*[/]" + base + "_" + climate + "(?:\\d+)?(?:_blink2?)?\\.png")
                        && modelResources.exists(climate + "_" + base, List.of())) {
                    base = climate + "_" + base;
                    break;
                }
            }
        }
        if (isChild(entity) && modelResources.exists(base + "_baby", List.of())) {
            base += "_baby";
        }
        if (path.contains("pig_saddle")) return childLayerName(base, "pig", "pig_saddle");
        if (path.contains("sheep_fur") || path.contains("sheep_wool")) return childLayerName(base, "sheep", "sheep_wool");
        if (path.contains("wolf_collar")) return childLayerName(base, "wolf", "wolf_collar");
        if (path.contains("creeper_armor")) return childLayerName(base, "creeper", "creeper_charge");
        if (path.contains("horse/armor") || path.contains("horse_armor")) return "horse_armor";
        if (renderPass && renderer.getClass().getSimpleName().contains("Gelatinous")) return "slime_outer";
        if (renderPass && path.contains("models/armor/")) {
            return base + (path.contains("_layer_2") ? "_inner_armor" : "_outer_armor");
        }
        return base;
    }

    private static String childLayerName(String base, String parent, String layer) {
        return base.equals(parent + "_baby") ? layer.replace(parent, parent + "_baby") : layer;
    }

    private static String baseEntityName(Entity entity) {
        if (entity instanceof EntityPlayer) {
            return "player";
        }
        if (entity instanceof EntitySkeleton skeleton && skeleton.getSkeletonType() == 1) {
            return "wither_skeleton";
        }
        if (entity instanceof EntityPigZombie) {
            return "zombified_piglin";
        }
        if (entity instanceof EntityOcelot cat) return cat.getTameSkin() == 0 ? "ocelot" : "cat";
        if (entity instanceof EntityZombie zombie && zombie.isVillager()) {
            return "zombie_villager";
        }
        if (entity instanceof EntityHorse horse) {
            return switch (horse.getHorseType()) {
                case 1 -> "donkey";
                case 2 -> "mule";
                case 3 -> "zombie_horse";
                case 4 -> "skeleton_horse";
                default -> "horse";
            };
        }
        String simple = entity.getClass().getSimpleName();
        if (simple.startsWith("Entity")) simple = simple.substring(6);
        String snake = simple.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return switch (snake) {
            case "snowman" -> "snow_golem";
            case "giant_zombie" -> "giant";
            case "mooshroom" -> "mooshroom";
            case "gelatinous_cube" -> "slime";
            case "ender_crystal" -> "end_crystal";
            case "leash_knot" -> "lead_knot";
            case "minecart_chest", "minecart_furnace", "minecart_t_n_t", "minecart_hopper",
                    "minecart_mob_spawner", "minecart_empty" -> "minecart";
            default -> snake;
        };
    }

    private static boolean isChild(Entity entity) {
        return entity instanceof EntityAgeable ageable && ageable.isChild() ||
                entity instanceof EntityLiving living && living.isChild();
    }

    private <T> T readJson(ResourceLocation location, Class<T> type) {
        ResourceManager manager = resources;
        if (manager == null && Minecraft.getMinecraft() != null) {
            manager = Minecraft.getMinecraft().getResourceManager();
        }
        if (manager == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(manager.getResource(location).getInputStream(), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException | RuntimeException exception) {
            recordLoadingError(location, exception);
            return null;
        }
    }

    private void recordLoadingError(ResourceLocation location, Exception exception) {
        String detail = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (loadingErrors.putIfAbsent(location, detail) != null) return;
        System.err.println("[EMF] Failed to load " + location + ": " + detail);
        if (EMFConfig.get().showReloadErrorToast) {
            printDebugLine("Model load failed: " + location + " (see log for details)");
        }
    }

    private ResourceLocation resolveTexture(ResourceLocation owner, String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }
        ResourceLocation texture = resolveRelative(owner, rawPath, ".png");
        return ETFApi.resourceExists(texture) ? texture : null;
    }

    private static ResourceLocation resolveRelative(ResourceLocation owner, String rawPath, String extension) {
        String path = rawPath.trim().replace('\\', '/');
        if (!path.endsWith(extension)) path += extension;
        String domain = owner.getResourceDomain();
        int namespace = path.indexOf(':');
        if (namespace > 0) {
            domain = path.substring(0, namespace);
            path = path.substring(namespace + 1);
        } else if (path.startsWith("~/")) {
            path = "optifine/" + path.substring(2);
        } else if (path.startsWith("./")) {
            path = parent(owner.getResourcePath()) + path.substring(2);
        } else if (!path.contains("/")) {
            path = parent(owner.getResourcePath()) + path;
        }
        return new ResourceLocation(domain, path, false);
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    private static ResourceLocation numbered(ResourceLocation base, int suffix) {
        String path = base.getResourcePath();
        int extension = path.lastIndexOf('.');
        return new ResourceLocation(base.getResourceDomain(), path.substring(0, extension) + suffix + path.substring(extension), false);
    }

    private static ResourceLocation replaceExtension(ResourceLocation base, String extension) {
        String path = base.getResourcePath();
        int old = path.lastIndexOf('.');
        return new ResourceLocation(base.getResourceDomain(), path.substring(0, old) + extension, false);
    }
}
