package traben.entity_model_features.model;

import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.EntityVillager;
import net.minecraft.ModelBase;
import net.minecraft.ModelRenderer;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.animation.RenderParameters;
import traben.entity_texture_features.features.ETFRenderContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.compat.PoseManager;

public final class RenderContext {

    /**
     * A small, dependency-free bridge for client mods that need to contribute
     * vanilla player poses before a CEM model is drawn.  CEM intentionally
     * restores the legacy ModelRenderer pose before applying its own animation;
     * without this hook, third-person attack/use poses disappear on custom
     * player models.  The hook is deliberately expressed in terms of the
     * stable MITE model/entity classes so consumers do not need reflection.
     */
    @FunctionalInterface
    public interface ExternalPoseHook {

        void apply(ModelBase model, Entity entity, float partialTick);
    }

    /**
     * A client-owned transform applied at one model part's render tail, after
     * the vanilla/CEM pivot and before that part's geometry is drawn. The
     * callback must only multiply the current model-view matrix; EMF owns the
     * surrounding part scope and restores it after the callback returns. The
     * boolean result reports whether a transform was applied; it does not grant
     * the callback ownership of an additional matrix stack entry.
     */
    @FunctionalInterface
    public interface ExternalPartTransformHook {

        boolean apply(ModelBase model, Entity entity, ModelRenderer part, float scale, float partialTick);
    }

    private static final List<ExternalPoseHook> EXTERNAL_POSE_HOOKS = new CopyOnWriteArrayList<>();
    private static final List<ExternalPartTransformHook> EXTERNAL_PART_TRANSFORM_HOOKS = new CopyOnWriteArrayList<>();

    public static void registerExternalPoseHook(ExternalPoseHook hook) {
        if (hook != null && !EXTERNAL_POSE_HOOKS.contains(hook)) EXTERNAL_POSE_HOOKS.add(hook);
    }

    public static void registerExternalPartTransformHook(ExternalPartTransformHook hook) {
        if (hook != null && !EXTERNAL_PART_TRANSFORM_HOOKS.contains(hook)) {
            EXTERNAL_PART_TRANSFORM_HOOKS.add(hook);
        }
    }

    private static void applyExternalPoseHooks(ModelBase model, Entity entity, float partialTick) {
        for (ExternalPoseHook hook : EXTERNAL_POSE_HOOKS) hook.apply(model, entity, partialTick);
    }

    /**
     * Applies a transform at the model-part tail.  The caller must already be
     * inside that part's vanilla/CEM pivot transform and owns the surrounding
     * matrix scope.  This mirrors modern ModelPart tail hooks and is required
     * for replacement CEM parts: applying the transform before CustomModel
     * renders the part would be overwritten by the part's own pose.
     */
    static boolean applyExternalPartTransformHooks(ModelBase model, Entity entity, ModelRenderer part,
                                                    float scale, float partialTick) {
        if (ETFRenderContext.isFirstPersonHand()) return false;
        boolean applied = false;
        for (ExternalPartTransformHook hook : EXTERNAL_PART_TRANSFORM_HOOKS) {
            applied |= hook.apply(model, entity, part, scale, partialTick);
        }
        return applied;
    }

    private static final class State {

        private final CustomModel model;
        private final ModelBase vanillaModel;
        private final RenderParameters parameters;
        private final boolean isolated;
        private final boolean renderPass;
        private final PlayerBipedPose inheritedPlayerPose;
        private boolean inheritedRootPushed;
        private boolean legacyBabyScalePushed;
        private int partDepth;
        private Map<ModelBase, CustomModel> savedSelectedModels;

        private State(CustomModel model, ModelBase vanillaModel, RenderParameters parameters,
                      boolean isolated, boolean renderPass, PlayerBipedPose inheritedPlayerPose) {
            this.model = model;
            this.vanillaModel = vanillaModel;
            this.parameters = parameters;
            this.isolated = isolated;
            this.renderPass = renderPass;
            this.inheritedPlayerPose = inheritedPlayerPose;
        }
    }

    private static final ThreadLocal<Deque<State>> STATES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ModelRenderer> BYPASS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUSPENDED = ThreadLocal.withInitial(() -> false);

    public static boolean isSuspended() { return SUSPENDED.get(); }

    /** Exposes the ETF hand-render scope without making bridge mods depend on ETF internals. */
    public static boolean isFirstPersonHand() { return ETFRenderContext.isFirstPersonHand(); }

    /** Exposes the GUI preview scope to bridge mods that add player render transforms. */
    public static boolean isInGui() { return ETFRenderContext.isInGui(); }

    public static float guiPreviewLimbSwing(Entity entity, float limbSwing) {
        return isGuiPlayerPreview(entity) ? 0.0F : limbSwing;
    }

    public static float guiPreviewLimbSpeed(Entity entity, float limbSpeed) {
        return isGuiPlayerPreview(entity) ? 0.0F : limbSpeed;
    }

    public static float guiPreviewAge(Entity entity, float age) {
        // GuiInventory renders with partialTick=1; model animation still needs frame interpolation.
        return isGuiPlayerPreview(entity)
                ? entity.ticksExisted + EMFManager.INSTANCE.getTickDelta()
                : age;
    }

    private static boolean isGuiPlayerPreview(Entity entity) {
        return entity instanceof EntityPlayer && ETFRenderContext.isInGui();
    }

    private static final class EntityState {

        private Entity entity;
        private EMFManager.RenderValues savedRenderValues;
        private Map<ModelBase, CustomModel> savedSelectedModels;
        private CustomModel model;
        private RenderParameters parameters;
        private final Map<String, float[]> attachments = new HashMap<>();
        private final Map<String, float[]> playerArmTransforms = new HashMap<>();
        private final Map<CustomModel, RenderParameters> animations = new IdentityHashMap<>();
        private final Map<Integer, CustomModel> passModels = new HashMap<>();
        private PlayerBipedPose playerPose;
        private int passIndex;
        private boolean debug;
    }

    private static final ThreadLocal<Deque<EntityState>> ENTITIES = ThreadLocal.withInitial(ArrayDeque::new);

    public static Entity currentEntity() {
        EntityState state = ENTITIES.get().peek();
        return state == null ? ETFRenderContext.currentEntity() : state.entity;
    }

    public static CustomModel currentModel() {
        State state = STATES.get().peek();
        return state == null ? null : state.model;
    }

    /** Keep isChild available to expressions, but skip legacy render-level matrices for baked CEM babies. */
    public static boolean useLegacyChildTransform(boolean child) {
        CustomModel model = isSuspended() ? null : currentModel();
        return child && (model == null || !model.hasBakedBabyPose());
    }

    public static float currentPartialTick() {
        State state = STATES.get().peek();
        if (state != null) return state.parameters.partialTickValue();
        // Held-item attachments are rendered by RenderPlayer after the
        // ModelBase draw scope has ended.  The entity scope still owns the
        // exact RenderParameters used for that draw, so attachments must use
        // its partial tick instead of silently falling back to frame zero.
        EntityState entity = ENTITIES.get().peek();
        return entity == null || entity.parameters == null ? 0.0F : entity.parameters.partialTickValue();
    }

    public static void begin(Object renderer, ModelBase model, Entity entity, boolean renderPass,
                             float limbSwing, float limbSpeed, float age, float headYaw, float headPitch, float scale) {
        begin(renderer, model, entity, renderPass, null, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
    }

    public static void beginNamed(Object renderer, ModelBase model, Entity entity, boolean renderPass,
                                  String modelName, float limbSwing, float limbSpeed, float age,
                                  float headYaw, float headPitch, float scale) {
        begin(renderer, model, entity, renderPass, modelName, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
    }

    public static void beginIsolatedNamed(Object renderer, ModelBase model, Entity entity, String modelName,
                                         float limbSwing, float limbSpeed, float age, float headYaw,
                                         float headPitch, float scale) {
        beginEntity(entity);
        begin(renderer, model, entity, false, modelName, limbSwing, limbSpeed, age, headYaw, headPitch, scale, true);
    }

    private static void begin(Object renderer, ModelBase model, Entity entity, boolean renderPass,
                              String modelName, float limbSwing, float limbSpeed, float age,
                              float headYaw, float headPitch, float scale) {
        begin(renderer, model, entity, renderPass, modelName, limbSwing, limbSpeed, age, headYaw, headPitch, scale, false);
    }

    private static void begin(Object renderer, ModelBase model, Entity entity, boolean renderPass,
                              String modelName, float limbSwing, float limbSpeed, float age,
                              float headYaw, float headPitch, float scale, boolean isolated) {
        Map<ModelBase, CustomModel> saved = STATES.get().isEmpty() ? null : EMFManager.INSTANCE.captureSelectedModels();
        EntityState entityState = ENTITIES.get().peek();
        CustomModel custom;
        boolean cachedPass = renderPass && modelName == null && entityState != null;
        if (cachedPass && entityState.passModels.containsKey(entityState.passIndex)) {
            custom = entityState.passModels.get(entityState.passIndex);
        } else {
            custom = EMFManager.INSTANCE.getModel(renderer, model, entity, renderPass, modelName);
            if (cachedPass) entityState.passModels.put(entityState.passIndex, custom);
        }
        boolean playerArmorPass = EMFManager.INSTANCE.isPlayerArmorPass(renderer, entity, renderPass, modelName);
        PlayerBipedPose inheritedPlayerPose = null;
        if (playerArmorPass
                && entityState != null && entityState.playerPose != null
                && (custom == null || !custom.hasAnimatedRoot())) {
            inheritedPlayerPose = entityState.playerPose;
        }
        if (custom != null && entity instanceof EntityPlayer
                && EMFConfig.get().resetPlayerModelEachRender_v2
                && !ETFRenderContext.isFirstPersonHand()) {
            custom.resetVanillaPartsToDefaults(playerArmorPass);
        }
        RenderParameters parameters = new RenderParameters(entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        if (custom != null) parameters.setModelRuleIndex(EMFManager.INSTANCE.modelRuleIndex(entity, custom.modelName()));
        if (custom != null && entityState != null) {
            RenderParameters existing = entityState.animations.putIfAbsent(custom, parameters);
            if (existing != null) parameters = existing;
        }
        parameters.beginDraw();
        State state = new State(custom, model, parameters, isolated, renderPass, inheritedPlayerPose);
        // RenderVillager applies its baby .5 scale outside ModelBase.render.
        // Replace that scale only after a CEM model was successfully selected.
        if (custom != null && custom.hasBakedBabyPose() && entity instanceof EntityVillager villager && villager.isChild()) {
            GL11.glPushMatrix();
            GL11.glScalef(2, 2, 2);
            state.legacyBabyScalePushed = true;
        }
        if (custom == null && inheritedPlayerPose != null && inheritedPlayerPose.rootPose() != null) {
            GL11.glPushMatrix();
            inheritedPlayerPose.rootPose().apply(scale);
            state.inheritedRootPushed = true;
        }
        STATES.get().push(state);
        STATES.get().peek().savedSelectedModels = saved;
        if (!renderPass && custom != null) {
            EntityState entityRenderState = ENTITIES.get().peek();
            if (entityRenderState != null && entityRenderState.parameters == null) {
                entityRenderState.model = custom;
                entityRenderState.parameters = STATES.get().peek().parameters;
            }
        }
    }

    public static void end() {
        Deque<State> states = STATES.get();
        if (!states.isEmpty()) {
            State state = states.pop();
            try {
                state.parameters.restoreVanillaPoses();
                if (state.savedSelectedModels != null) EMFManager.INSTANCE.restoreSelectedModels(state.savedSelectedModels);
                if (state.isolated) endEntity();
            } finally {
                if (state.inheritedRootPushed) GL11.glPopMatrix();
                if (state.legacyBabyScalePushed) GL11.glPopMatrix();
            }
        }
        if (states.isEmpty()) {
            STATES.remove();
        }
        BYPASS.remove();
    }

    /** An independently named feature shares the entity inputs, but owns its animation and draw scope. */
    public static boolean beginFeature(Object renderer, ModelBase model, Entity entity, String name, float scale) {
        Map<ModelBase, CustomModel> saved = EMFManager.INSTANCE.captureSelectedModels();
        EntityState entityState = ENTITIES.get().peek();
        CustomModel custom = EMFManager.INSTANCE.getModel(renderer, model, entity, true, name);
        if (custom == null) {
            EMFManager.INSTANCE.restoreSelectedModels(saved);
            return false;
        }
        RenderParameters parameters = entityState != null && entityState.parameters != null
                ? entityState.parameters.copyInputs(scale)
                : new RenderParameters(entity, 0, 0, entity.ticksExisted, 0, 0, scale);
        parameters.setModelRuleIndex(EMFManager.INSTANCE.modelRuleIndex(entity, custom.modelName()));
        if (entityState != null) {
            RenderParameters previous = entityState.animations.putIfAbsent(custom, parameters);
            if (previous != null) parameters = previous;
        }
        parameters.beginDraw();
        STATES.get().push(new State(custom, model, parameters, false, true, null));
        STATES.get().peek().savedSelectedModels = saved;
        return true;
    }

    public static void beginEntity() {
        beginEntity(ETFRenderContext.currentEntity());
    }

    public static void beginEntity(Entity entity) {
        EntityState state = new EntityState();
        if (!ENTITIES.get().isEmpty()) state.savedSelectedModels = EMFManager.INSTANCE.captureSelectedModels();
        state.entity = entity;
        if (entity instanceof EntityPlayer) PoseManager.beginEntity(entity);
        state.debug = EMFManager.INSTANCE.beginDebug(entity);
        for (EntityState parent : ENTITIES.get()) {
            if (parent.entity == entity) {
                state.savedRenderValues = EMFManager.INSTANCE.captureRenderVariables(entity);
                break;
            }
        }
        ENTITIES.get().push(state);
        EMFManager.INSTANCE.resetRenderVariables(entity);
    }

    public static void setPassIndex(int index) {
        EntityState entity = ENTITIES.get().peek();
        if (entity != null) entity.passIndex = index;
    }

    public static void endEntity() {
        Deque<EntityState> entities = ENTITIES.get();
        if (!entities.isEmpty()) {
            EntityState state = entities.pop();
            if (state.savedSelectedModels != null) EMFManager.INSTANCE.restoreSelectedModels(state.savedSelectedModels);
            if (state.savedRenderValues != null)
                EMFManager.INSTANCE.restoreRenderVariables(state.entity, state.savedRenderValues);
            if (state.debug) EMFManager.INSTANCE.printDebug(state.entity);
            if (state.entity instanceof EntityPlayer) PoseManager.endEntity(state.entity);
        }
        if (entities.isEmpty()) ENTITIES.remove();
    }

    public static boolean postRender(ModelRenderer part, float scale) {
        if (isSuspended()) return false;
        EntityState state = ENTITIES.get().peek();
        if (state == null || state.model == null) return false;
        state.parameters.reapplyVanillaOverrides();
        state.parameters.reapplyCustomPose();
        // NEA changes the vanilla biped pose after the base model has been
        // prepared.  The third-person held-item path reaches us through
        // ModelRenderer.postRender, after the normal model render has
        // already restored the vanilla parts.  Reapply the external action
        // layer here as well so the item attachment uses the same arm pose as
        // the custom EMF arm geometry.
        Entity poseEntity = currentEntity();
        if (poseEntity instanceof EntityPlayer && !ETFRenderContext.isFirstPersonHand()) {
            applyExternalPoseHooks(state.model.drawingModel(), poseEntity, state.parameters.partialTickValue());
        }
        try {
            if (state.model.postRender(part, scale)) {
                // Keep Punchy's third-person tail delta on the same matrix
                // used by the fallback attachment path.  Without this, the
                // arm can be animated while the held item remains on the
                // unanimated vanilla tail.
                applyExternalPartTransformHooks(state.model.drawingModel(), poseEntity, part, scale,
                        state.parameters.partialTickValue());
                return true;
            }
            if (state.parameters.hasExtendedPose(part)) {
                if (part.showModel && !part.isHidden) CustomModel.applyVanillaTransform(part, scale, false, false);
                return true;
            }
            return false;
        }
        finally { state.parameters.restoreVanillaPoses(); }
    }

    public static boolean render(ModelRenderer part, float scale) {
        return render(part, scale, false);
    }

    public static boolean renderBridgedModel(ModelBase source, Entity entity, float limbSwing,
                                             float limbSpeed, float age, float headYaw, float headPitch, float scale) {
        if (isSuspended()) return false;
        State state = currentState();
        if (state == null || state.model == null || !state.model.bridges(source)) return false;
        ModelBase drawing = state.model.drawingModel();
        drawing.onGround = source.onGround;
        drawing.isRiding = source.isRiding;
        drawing.isChild = source.isChild;
        drawing.render(entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        return true;
    }

    public static boolean render(ModelRenderer part, float scale, boolean rotated) {
        if (isSuspended()) return false;
        if (BYPASS.get() == part) {
            BYPASS.remove();
            return false;
        }
        State state = currentState();
        if (state == null) return false;
        if (state.model == null) {
            if (state.inheritedPlayerPose == null) return false;
            boolean outerPart = state.partDepth++ == 0;
            try {
                if (outerPart) {
                    state.inheritedPlayerPose.applyTo(state.vanillaModel, null, state.parameters);
                }
            } finally {
                state.partDepth--;
            }
            return false;
        }
        boolean outerPart = state.partDepth++ == 0;
        state.model.animate(state.parameters);
        state.parameters.reapplyVanillaOverrides();
        Entity poseEntity = currentEntity();
        // CEM re-applies its vanilla overrides for every top-level ModelRenderer.
        // Reapply external player action poses at the same boundary, otherwise
        // the first part (usually the head) receives the action but subsequent
        // body/arm parts are reset back to the CEM-only pose.
        if (outerPart && poseEntity instanceof EntityPlayer
                && !ETFRenderContext.isFirstPersonHand()) {
            applyExternalPoseHooks(state.model.drawingModel(), poseEntity, state.parameters.partialTickValue());
        }
        if (outerPart && !state.renderPass && poseEntity instanceof EntityPlayer
                && !ETFRenderContext.isFirstPersonHand() && "player".equals(state.model.modelName())) {
            EntityState entityState = ENTITIES.get().peek();
            if (entityState != null && entityState.playerPose == null) {
                entityState.playerPose = PlayerBipedPose.capture(
                        state.model.drawingModel(), state.model, state.parameters);
            }
        }
        if (outerPart && state.inheritedPlayerPose != null) {
            state.inheritedPlayerPose.applyTo(state.model.drawingModel(), state.model, state.parameters);
        }
        if (outerPart) renderHologram(part, scale, rotated, state);
        CustomModel.RenderStart start = outerPart ? state.model.beginRender(scale, state.parameters,
                state.inheritedPlayerPose == null ? null : state.inheritedPlayerPose.rootPose())
                : new CustomModel.RenderStart(true, false);
        try {
            if (start.render()) {
                if (state.model.handles(part)) {
                    state.model.render(part, scale, state.parameters, rotated);
                } else {
                    bypassOnce(part);
                    renderVanilla(part, scale, rotated);
                }
            }
        } finally {
            state.model.endRender(start.rootTransformPushed());
            state.partDepth--;
        }
        return true;
    }

    static void bypassOnce(ModelRenderer part) {
        BYPASS.set(part);
    }

    static void renderVanilla(ModelRenderer part, float scale, boolean rotated) {
        if (rotated) part.renderWithRotation(scale); else part.render(scale);
    }

    private static void renderHologram(ModelRenderer part, float scale, boolean rotated, State state) {
        EMFConfig.VanillaModelRenderMode mode = EMFConfig.get().getVanillaHologramModeFor(currentEntity());
        if (mode == EMFConfig.VanillaModelRenderMode.OFF) return;
        state.parameters.restoreVanillaPoses();
        SUSPENDED.set(true);
        GL11.glPushMatrix();
        try {
            if (mode == EMFConfig.VanillaModelRenderMode.OFFSET) GL11.glTranslatef(1, 0, 0);
            renderVanilla(part, scale, rotated);
        } finally {
            GL11.glPopMatrix();
            SUSPENDED.remove();
            state.parameters.reapplyVanillaOverrides();
        }
    }

    public static boolean renderExtended(ModelRenderer part, float scale, boolean rotated, PartGeometry geometry) {
        if (isSuspended()) return false;
        RenderParameters parameters = activeParameters();
        if (parameters == null || !parameters.hasExtendedPose(part)) return false;
        if (part.isHidden || !part.showModel) return true;
        GL11.glPushMatrix();
        try {
            CustomModel.applyVanillaTransform(part, scale, !rotated, rotated);
            if (parameters.vanillaValue(part, "visible_boxes") != 0) geometry.emf$drawBoxes(scale);
            if (!rotated && part.childModels != null)
                for (Object child : part.childModels) ((ModelRenderer) child).render(scale);
        } finally { GL11.glPopMatrix(); }
        return true;
    }

    static void applyVanillaScale(ModelRenderer part) {
        RenderParameters parameters = activeParameters();
        if (parameters != null) GL11.glScalef((float) parameters.vanillaValue(part, "sx"),
                (float) parameters.vanillaValue(part, "sy"), (float) parameters.vanillaValue(part, "sz"));
    }

    private static RenderParameters activeParameters() {
        State state = currentState();
        if (state != null) return state.parameters;
        EntityState entity = ENTITIES.get().peek();
        return entity == null ? null : entity.parameters;
    }

    static void recordAttachment(String name) {
        EntityState state = ENTITIES.get().peek();
        State modelState = currentState();
        if (state != null && modelState != null && !modelState.renderPass &&
                state.parameters == modelState.parameters) state.attachments.put(name, currentMatrix());
    }

    static void recordPlayerArmTransform(ModelBase model, Entity entity, ModelRenderer part) {
        EntityState entityState = ENTITIES.get().peek();
        State modelState = currentState();
        if (!(entity instanceof EntityPlayer) || entityState == null || modelState == null
                || modelState.renderPass || entityState.entity != entity || entityState.model != modelState.model
                || modelState.parameters != entityState.parameters || modelState.model == null
                || modelState.model.drawingModel() != model || ETFRenderContext.isFirstPersonHand()) return;

        String target = part == modelState.model.findVanillaPart("right_arm") ? "right_arm"
                : part == modelState.model.findVanillaPart("left_arm") ? "left_arm" : null;
        if (target != null) recordPlayerArmTransform(target);
    }

    static void recordPlayerArmTransform(String target) {
        EntityState entityState = ENTITIES.get().peek();
        State modelState = currentState();
        if (entityState == null || modelState == null || !(entityState.entity instanceof EntityPlayer)
                || modelState.renderPass || entityState.model != modelState.model
                || modelState.parameters != entityState.parameters || modelState.model == null
                || ETFRenderContext.isFirstPersonHand()) return;

        String attachment = target.equals("right_arm") ? "right_handheld_item"
                : target.equals("left_arm") ? "left_handheld_item" : null;
        // Legacy RenderPlayer asks for the hand after ModelBiped.render has restored its pose fields.
        // A replacing CustomPart can subsequently overwrite this fallback with its
        // animation delta, while native/unsupported arm models retain this snapshot.
        if (attachment != null) entityState.playerArmTransforms.put(attachment, currentMatrix());
    }

    static void recordPlayerArmTransform(String target, float scale, float tx, float ty, float tz,
                                         float rx, float ry, float rz, float sx, float sy, float sz) {
        EntityState entityState = ENTITIES.get().peek();
        State modelState = currentState();
        if (entityState == null || modelState == null || !(entityState.entity instanceof EntityPlayer)
                || modelState.renderPass || entityState.model != modelState.model
                || modelState.parameters != entityState.parameters || modelState.model == null
                || ETFRenderContext.isFirstPersonHand()) return;

        String attachment = target.equals("right_arm") ? "right_handheld_item"
                : target.equals("left_arm") ? "left_handheld_item" : null;
        if (attachment == null) return;
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(tx * scale, ty * scale, tz * scale);
            if (rz != 0.0F) GL11.glRotatef((float) Math.toDegrees(rz), 0, 0, 1);
            if (ry != 0.0F) GL11.glRotatef((float) Math.toDegrees(ry), 0, 1, 0);
            if (rx != 0.0F) GL11.glRotatef((float) Math.toDegrees(rx), 1, 0, 0);
            if (sx != 1.0F || sy != 1.0F || sz != 1.0F) GL11.glScalef(sx, sy, sz);
            entityState.playerArmTransforms.put(attachment, currentMatrix());
        } finally {
            GL11.glPopMatrix();
        }
    }

    public static boolean applyAttachment(String name, float scale, boolean replace) {
        EntityState state = ENTITIES.get().peek();
        if (state == null || state.model == null) return false;
        if (replace) {
            float[] matrix = state.attachments.get(name);
            if (matrix != null) { loadMatrix(matrix); return true; }
            matrix = state.playerArmTransforms.get(name);
            if (matrix != null) { loadMatrix(matrix); return true; }
        }
        state.parameters.reapplyVanillaOverrides();
        state.parameters.reapplyCustomPose();
        Entity poseEntity = currentEntity();
        if (poseEntity instanceof EntityPlayer && !ETFRenderContext.isFirstPersonHand()) {
            // The attachment call happens after ModelBiped.render has
            // unwound its temporary pose.  Rebuild the same NEA action layer
            // before EMF walks the arm path, otherwise the held item follows
            // a different transform than the visible arm.
            applyExternalPoseHooks(state.model.drawingModel(), poseEntity, state.parameters.partialTickValue());
        }
        try { return state.model.applyAttachment(name, scale); }
        finally { state.parameters.restoreVanillaPoses(); }
    }

    private static float[] currentMatrix() {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buffer);
        float[] matrix = new float[16];
        buffer.get(matrix);
        return matrix;
    }

    private static void loadMatrix(float[] matrix) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(matrix).flip();
        GL11.glLoadMatrix(buffer);
    }

    private static State currentState() {
        return STATES.get().peek();
    }
}
