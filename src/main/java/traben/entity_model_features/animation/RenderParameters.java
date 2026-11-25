package traben.entity_model_features.animation;

import net.minecraft.Entity;
import net.minecraft.EntityAgeable;
import net.minecraft.EntityPlayer;
import net.minecraft.EntityLiving;
import net.minecraft.EntityLivingBase;
import net.minecraft.EntityTameable;
import net.minecraft.EntityPigZombie;
import net.minecraft.Block;
import net.minecraft.Minecraft;
import net.minecraft.MathHelper;
import net.minecraft.ModelRenderer;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.compat.ModCompat;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.model.CustomModel;
import traben.entity_model_features.model.CustomPart;
import traben.entity_model_features.mixin.EntityPigZombieMixin;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_texture_features.features.NbtMatcher;
import traben.entity_texture_features.features.player.ModernPlayerModel;
import moddedmite.rustedironcore.api.entity.EntityIdentity;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.IdentityHashMap;

public final class RenderParameters {

    private final Entity entity;
    private final EntityLivingBase living;
    private final float limbSwing;
    private final float limbSpeed;
    private final float age;
    private final float headYaw;
    private final float headPitch;
    private final float scale;
    private boolean animated;
    private boolean rootRendered;
    private Map<ModelRenderer, String> vanillaParts;
    private CustomModel model;
    private int modelRuleIndex;
    private final Map<ModelRenderer, VanillaPose> originalPoses = new IdentityHashMap<>();
    private final Map<ModelRenderer, Map<String, Double>> vanillaOverrides = new IdentityHashMap<>();
    private final Map<CustomPart, CustomPose> customPoses = new IdentityHashMap<>();
    private final Map<String, ModelRenderer> renderedTargetOwners = new HashMap<>();
    private final Map<String, Double> renderOverrides = new HashMap<>();
    private static final Map<String, java.util.function.Predicate<Entity>> NBT_QUERIES = new HashMap<>();

    public RenderParameters(Entity entity, float limbSwing, float limbSpeed, float age,
                            float headYaw, float headPitch, float scale) {
        this.entity = entity;
        this.living = entity instanceof EntityLivingBase base ? base : null;
        if (ETFRenderContext.isInGui() && this.living != null) {
            double delta = partialTick();
            // Inventory previews render the live player entity, but should not
            // inherit its changing world locomotion phase.
            this.limbSwing = 0.0F;
            this.limbSpeed = 0.0F;
            this.age = entity.ticksExisted + (float) delta;
        } else {
            this.limbSwing = limbSwing;
            this.limbSpeed = limbSpeed;
            this.age = age;
        }
        // Match upstream EMF's [-180, 180) animation input. Legacy body-yaw
        // interpolation can leave whole turns in the head/body difference,
        // especially when GuiInventory temporarily replaces the body angle.
        this.headYaw = headYaw >= 180.0F || headYaw < -180.0F
                ? MathHelper.wrapAngleTo180_float(headYaw) : headYaw;
        this.headPitch = headPitch;
        this.scale = scale;
    }

    public void animate(CustomModel model, List<AnimationProgram.Assignment> assignments,
                        List<CustomPart> customParts, Map<ModelRenderer, String> vanillaParts) {
        if (animated) {
            reapplyCustomPose();
            renderOverrides.forEach(this::applyRenderValue);
            return;
        }
        animated = true;
        this.model = model;
        this.vanillaParts = vanillaParts;
        model.resetPose();
        if (isChild()) model.applyBabyPose(this);
        Map<String, Double> history = ETFRenderContext.isInGui()
                ? EMFManager.INSTANCE.guiAnimationHistory(entity, model)
                : EMFManager.INSTANCE.animationHistory(entity, model);
        boolean pauseAll = AnimationControl.isPaused(entity) || EMFConfig.get().pauseFirstPerson()
                || !history.isEmpty() && AnimationLOD.skip(entity, model);
        for (AnimationProgram.Assignment assignment : assignments) {
            String target = assignment.target();
            if (pauseAll || AnimationControl.partPaused(entity, model.targetPart(target))) {
                if (!target.startsWith("var.") && !target.startsWith("varb.")
                        && !target.startsWith("global_var.") && !target.startsWith("global_varb.")
                        && history.containsKey(target)) set(target, history.get(target));
            } else {
                double value = assignment.expression().evaluate(this);
                if (!Double.isNaN(value)) {
                    set(target, value);
                    history.put(target, value);
                }
            }
        }
        customParts.forEach(part -> customPoses.put(part, new CustomPose(part)));
    }

    public RenderParameters copyInputs(float featureScale) {
        return new RenderParameters(entity, limbSwing, limbSpeed, age, headYaw, headPitch, featureScale);
    }

    public void setModelRuleIndex(int ruleIndex) { modelRuleIndex = ruleIndex; }

    /** Each draw has its own geometry and restoration scope; animation is evaluated once per entity render. */
    public void beginDraw() {
        originalPoses.clear();
        renderedTargetOwners.clear();
        rootRendered = false;
    }

    public double get(String name) {
        return switch (name) {
            case "pi" -> Math.PI;
            case "e" -> Math.E;
            case "true" -> 1.0D;
            case "false" -> 0.0D;
            case "nan" -> Double.NaN;
            case "limb_swing" -> limbSwing;
            // MITE keeps the run toggle separate from Entity's sprint flag while
            // creative flight is active.  Fresh Animations uses limb_speed for
            // its flight pose, so mirror the faster flight movement in the
            // animation input instead of leaving sprint-flight visually
            // identical to normal flight.
            case "limb_speed" -> effectiveLimbSpeed();
            case "age" -> age;
            case "time" -> entity.worldObj == null ? age : entity.worldObj.getTotalWorldTime() % 31415L + partialTick();
            case "head_yaw" -> headYaw;
            case "head_pitch" -> headPitch;
            case "frame_time" -> EMFManager.INSTANCE.getFrameTime();
            case "frame_counter" -> EMFManager.INSTANCE.getFrameCounter();
            case "health" -> living == null ? 0 : living.getHealth();
            case "max_health" -> living == null ? 0 : living.getMaxHealth();
            case "hurt_time" -> living == null ? 0 : Math.max(0, living.hurtTime - partialTick());
            case "death_time" -> living == null ? 0 : living.deathTime > 0 ? living.deathTime + partialTick() : 0;
            case "swing_progress" -> living == null || guiMotionDisabled() ? 0 : living.getSwingProgress((float) partialTick());
            case "pos_x" -> MotionMath.lerp(entity.prevPosX, entity.posX, partialTick());
            case "pos_y" -> MotionMath.lerp(entity.prevPosY, entity.posY, partialTick());
            case "pos_z" -> MotionMath.lerp(entity.prevPosZ, entity.posZ, partialTick());
            case "rot_x" -> ETFRenderContext.isInGui() ? Math.toRadians(entity.rotationPitch)
                    : MotionMath.angle(entity.prevRotationPitch, entity.rotationPitch, partialTick());
            case "rot_y" -> living == null ? MotionMath.angle(entity.prevRotationYaw, entity.rotationYaw, partialTick())
                    : ETFRenderContext.isInGui() ? Math.toRadians(living.renderYawOffset)
                    : MotionMath.angle(living.prevRenderYawOffset, living.renderYawOffset, partialTick());
            case "move_forward" -> movement(false);
            case "move_strafing" -> movement(true);
            case "fluid_depth_down" -> fluidDepth(-1);
            case "fluid_depth_up" -> fluidDepth(1);
            case "fluid_depth" -> isInFluid() ? fluidDepth(-1) + fluidDepth(1) - 1 : 0;
            case "anger_time" -> anger(false);
            case "anger_time_start" -> anger(true);
            case "distance" -> distanceToPlayer();
            case "height_above_ground" -> heightAboveGround();
            case "is_climbing" -> bool(living != null && living.isOnLadder());
            case "is_blocking" -> bool(entity instanceof EntityPlayer player
                    && (ModCompat.isOffhandRenderContext() ? ModCompat.isOffhandBlocking(player) : player.isBlocking()));
            case "is_using_item" -> bool(entity instanceof EntityPlayer player
                    && (ModCompat.isOffhandRenderContext() ? ModCompat.isOffhandItemInUse(player) : player.isUsingItem()));
            case "is_holding_item" -> bool(isHoldingActiveHandItem());
            case "is_holding_item_right" -> bool(isHoldingActiveHandItem());
            case "is_holding_item_left" -> bool(entity instanceof EntityPlayer player && ModCompat.hasOffhandItem(player));
            case "is_swinging_left_arm" -> bool(entity instanceof EntityPlayer player
                    && ModCompat.getOffhandSwingProgress(player, (float) partialTick()) > 0.0F);
            case "is_swinging_right_arm" -> bool(ModCompat.isOffhandRenderContext() && entity instanceof EntityPlayer player
                    ? ModCompat.getOffhandSwingProgress(player, (float) partialTick()) > 0.0F
                    : living != null && living.isSwingInProgress);
            case "is_right_handed" -> bool(living != null);
            case "is_player_first_person" -> bool(Minecraft.getMinecraft().gameSettings.thirdPersonView == 0);
            case "is_player_third_person" -> bool(Minecraft.getMinecraft().gameSettings.thirdPersonView == 1);
            case "is_player_third_person_reversed" -> bool(Minecraft.getMinecraft().gameSettings.thirdPersonView == 2);
            case "is_paused" -> bool(Minecraft.getMinecraft().isGamePaused);
            case "is_first_person_hand" -> bool(ETFRenderContext.isFirstPersonHand());
            case "is_hovered" -> bool(Minecraft.getMinecraft().objectMouseOver != null
                    && Minecraft.getMinecraft().objectMouseOver.getEntityHit() == entity);
            case "dimension" -> entity.dimension;
            case "day_time" -> entity.worldObj == null ? 0.0D : entity.worldObj.getTimeOfDay();
            case "day_count" -> entity.worldObj == null ? 0.0D : entity.worldObj.getTotalWorldTime() / 24000L;
            case "rule_index" -> modelRuleIndex;
            case "render.shadow_size" -> EMFManager.INSTANCE.getShadowSize(entity);
            case "id" -> randomSeed();
            case "player_pos_x" -> playerCoordinate(0);
            case "player_pos_y" -> playerCoordinate(1);
            case "player_pos_z" -> playerCoordinate(2);
            case "player_rot_x" -> Minecraft.getMinecraft().thePlayer == null ? 0.0D
                    : ETFRenderContext.isInGui() ? Math.toRadians(Minecraft.getMinecraft().thePlayer.rotationPitch)
                    : MotionMath.angle(Minecraft.getMinecraft().thePlayer.prevRotationPitch,
                    Minecraft.getMinecraft().thePlayer.rotationPitch, partialTick());
            case "player_rot_y" -> Minecraft.getMinecraft().thePlayer == null ? 0.0D
                    : ETFRenderContext.isInGui() ? Math.toRadians(Minecraft.getMinecraft().thePlayer.rotationYaw)
                    : MotionMath.angle(Minecraft.getMinecraft().thePlayer.prevRotationYaw,
                    Minecraft.getMinecraft().thePlayer.rotationYaw, partialTick());
            case "is_aggressive" -> bool(entity instanceof EntityLiving living && living.getAttackTarget() != null);
            case "is_alive" -> bool(entity.isEntityAlive());
            case "is_burning" -> bool(entity.isBurning());
            case "is_child" -> bool(isChild());
            case "is_hurt" -> bool(living != null && living.hurtTime > 0);
            case "is_in_lava" -> bool(entity.handleLavaMovement());
            case "is_in_water" -> bool(entity.isInWater());
            case "is_invisible" -> bool(entity.isInvisible());
            case "is_on_ground" -> bool(entity.onGround);
            case "is_ridden" -> bool(entity.riddenByEntity != null);
            case "is_riding" -> bool(entity.isRiding());
            case "is_sitting" -> bool(entity instanceof EntityTameable tameable && tameable.isSitting());
            case "is_sneaking" -> bool(entity.isSneaking());
            case "is_sprinting" -> bool(effectiveSprinting());
            case "is_tamed" -> bool(entity instanceof EntityTameable tameable && tameable.isTamed());
            case "is_wet" -> bool(entity.isWet());
            case "is_jumping" -> bool(!entity.onGround && entity.motionY > 0.0D);
            case "is_in_gui" -> bool(ETFRenderContext.isInGui());
            case "is_in_hand" -> bool(ETFRenderContext.isInHand());
            case "is_in_item_frame" -> bool(ETFRenderContext.isInItemFrame());
            case "is_on_head" -> bool(ETFRenderContext.isOnHead());
            case "is_in_ground" -> bool(ETFRenderContext.isInGround());
            case "is_gliding" -> bool(entity instanceof EntityPlayer player && ModCompat.isElytraFlying(player));
            case "is_glowing", "is_on_shoulder", "is_crawling", "is_swimming" -> 0.0D;
            default -> dynamicValue(name);
        };
    }

    private boolean isHoldingActiveHandItem() {
        if (living == null) return false;
        if (entity instanceof EntityPlayer player && ModCompat.isOffhandRenderContext()) {
            return ModCompat.hasCurrentItem(player);
        }
        return living.getHeldItemStack() != null;
    }

    void set(String target, double value) {
        if (target == null || target.isEmpty()) {
            return;
        }
        // The upstream variable is clamped by Fresh Animations to zero while
        // var.flying is active.  That is correct for modern clients where
        // flying speed itself already changes, but MITE exposes the faster
        // flight state through its run toggle instead of isSprinting(). Keep
        // the resource unchanged and restore the semantic sprint signal here.
        if (target.equals("var.sprint") && effectiveSprinting() && isFlying()) {
            value = Math.max(value, 1.0D);
        }
        if (target.startsWith("var.") || target.startsWith("varb.")) {
            // Fresh Animations reads abilities.flying from player NBT. MITE keeps
            // that same authoritative state on PlayerCapabilities instead, so
            // feed var.flying's existing frame_time easing from the native flag.
            if (target.equals("var.is_flying") && isFlying()) {
                value = 1.0D;
            }
            EMFManager.INSTANCE.getEntityVariables(entity).put(target,
                    target.startsWith("varb.") ? bool(truth(value)) : value);
            return;
        }
        if (target.startsWith("global_var.") || target.startsWith("global_varb.")) {
            EMFManager.INSTANCE.getGlobalVariables().put(target,
                    target.startsWith("global_varb.") ? bool(truth(value)) : value);
            return;
        }
        if (target.equals("render.shadow_size")) {
            renderOverrides.put(target, value);
            applyRenderValue(target, value);
            return;
        }
        if (target.startsWith("render.")) {
            renderOverrides.put(target, value);
            applyRenderValue(target, value);
            return;
        }
        int separator = target.lastIndexOf('.');
        if (separator <= 0) {
            return;
        }
        String partName = target.substring(0, separator);
        String property = target.substring(separator + 1);
        ModelRenderer owner = findVanillaPart(partName);
        if (owner != null) {
            // ModernPlayerModel attaches the jacket, sleeves and pants to the
            // corresponding MITE limb.  Fresh Animations addresses those
            // layers with the parent's absolute pose, which is correct for a
            // flat modern model but would double-apply the pivot in MITE's
            // child hierarchy.  Leave the child local pose untouched so it
            // inherits the already animated parent transform.
            if (property.matches("[tr][xyz]|r[xyz]|s[xyz]"
                    ) && model != null && model.modelName().equals("player")
                    && model.drawingModel() instanceof ModernPlayerModel modern
                    && modern.isAttachedLayer(owner)) {
                return;
            }
            String canonicalName = vanillaParts.get(owner);
            boolean relative = property.matches("[tr][xyz]");
            double delta = relative ? value - getVanilla(owner, property) : 0;
            double assignedValue = value;
            if (vanillaParts != null) vanillaParts.forEach((vanilla, name) -> {
                if (name.equals(canonicalName) || mirrorsHeadTransform(canonicalName, name, relative)) {
                    double memberValue = relative ? getVanilla(vanilla, property) + delta : assignedValue;
                    originalPoses.computeIfAbsent(vanilla, VanillaPose::new);
                    vanillaOverrides.computeIfAbsent(vanilla, ignored -> new HashMap<>()).put(property, memberValue);
                    setVanilla(vanilla, property, memberValue);
                }
            });
            return;
        }
        CustomPart custom = findCustomPart(partName);
        if (custom != null) {
            setCustom(custom, property, value);
        }
    }

    /**
     * Legacy ModelBiped keeps the visible head shell in a sibling renderer.
     * OptiFine/modern biped models treat that shell as part of the head pivot,
     * so CEM animations addressed to head must move and rotate headwear too.
     */
    private static boolean mirrorsHeadTransform(String owner, String candidate, boolean relative) {
        return relative && owner.equals("head") && candidate.equals("headwear");
    }

    private void applyRenderValue(String name, double value) {
        if (name.equals("render.shadow_size")) EMFManager.INSTANCE.setShadowSize(entity, value);
        else EMFManager.INSTANCE.setRenderVariable(entity, name, value);
    }

    public double randomSeed() {
        if (entity == null) return 0;
        int value = (int) (EntityIdentity.uuid(entity).getLeastSignificantBits() & 0x7FFFFFFFL);
        return Math.abs(value) % 27720;
    }

    public boolean markRendered(String target, ModelRenderer part) {
        ModelRenderer owner = renderedTargetOwners.putIfAbsent(target, part);
        return owner == null || owner == part;
    }

    public boolean markRootRendered() {
        boolean first = !rootRendered;
        rootRendered = true;
        return first;
    }

    public void reapplyVanillaOverrides() {
        vanillaOverrides.forEach((part, values) -> {
            originalPoses.computeIfAbsent(part, VanillaPose::new);
            values.forEach((property, value) -> setVanilla(part, property, value));
        });
    }

    public void restoreVanillaPoses() {
        originalPoses.forEach((part, pose) -> pose.restore(part));
    }

    public void reapplyCustomPose() {
        customPoses.forEach((part, pose) -> pose.restore(part));
    }

    private record CustomPose(float x, float y, float z, float rx, float ry, float rz,
                              float sx, float sy, float sz, boolean visible, boolean boxes) {

        CustomPose(CustomPart part) {
            this(part.x, part.y, part.z, part.rx, part.ry, part.rz,
                    part.sx, part.sy, part.sz, part.visible, part.visibleBoxes);
        }

        void restore(CustomPart part) {
            part.x = x; part.y = y; part.z = z; part.rx = rx; part.ry = ry; part.rz = rz;
            part.sx = sx; part.sy = sy; part.sz = sz; part.visible = visible; part.visibleBoxes = boxes;
        }
    }

    private record VanillaPose(float x, float y, float z, float rx, float ry, float rz, boolean show, boolean hidden) {

        VanillaPose(ModelRenderer part) {
            this(part.rotationPointX, part.rotationPointY, part.rotationPointZ, part.rotateAngleX,
                    part.rotateAngleY, part.rotateAngleZ, part.showModel, part.isHidden);
        }

        void restore(ModelRenderer part) {
            part.rotationPointX = x; part.rotationPointY = y; part.rotationPointZ = z;
            part.rotateAngleX = rx; part.rotateAngleY = ry; part.rotateAngleZ = rz;
            part.showModel = show; part.isHidden = hidden;
        }
    }

    boolean matchesNbt(String path, String query) {
        return NBT_QUERIES.computeIfAbsent(path + "\u0000" + query,
                ignored -> NbtMatcher.compile(path, query)).test(entity);
    }

    boolean paused() {
        return Minecraft.getMinecraft().isGamePaused;
    }

    private double dynamicValue(String name) {
        if (name.startsWith("render.")) return EMFManager.INSTANCE.getRenderVariable(entity, name);
        if (name.startsWith("var.") || name.startsWith("varb.")) {
            // Fresh Animations deliberately clamps var.sprint to zero while
            // var.flying is active.  Modern clients expose a separate sprint
            // signal during flight, but MITE keeps that state in its run
            // toggle.  Return the MITE semantic value directly for this one
            // input so the flight pose does not depend on the resource's
            // ground/flight clamp or on a stale animation-history entry.
            if (name.equals("var.sprint") && isFlying()) {
                return effectiveSprinting() ? 1.0D : 0.0D;
            }
            Map<String, Double> variables = EMFManager.INSTANCE.getEntityVariables(entity);
            if (name.equals("var.body_ty") && model != null && model.modelName().equals("player_cape")) {
                // Fresh Animations' player_cape model is authored around the
                // CEM body's 24-unit anchor.  MITE's ModernPlayerModel keeps
                // the native body pivot at 0 and exposes the animated body
                // displacement through var.body_ty, so using that value here
                // directly lifts the cape root to the head (neutral pose:
                // 0).  The cape feature needs the fixed anchor plus the
                // optional body displacement instead.
                return 24.0D + variables.getOrDefault(name, 0.0D);
            }
            return variables.getOrDefault(name, 0.0D);
        }
        if (name.startsWith("global_var.") || name.startsWith("global_varb.")) {
            return EMFManager.INSTANCE.getGlobalVariables().getOrDefault(name, 0.0D);
        }
        int separator = name.lastIndexOf('.');
        if (separator <= 0) {
            return 0.0D;
        }
        String partName = name.substring(0, separator);
        String property = name.substring(separator + 1);
        ModelRenderer vanilla = findVanillaPart(partName);
        if (vanilla != null) {
            return vanillaValue(vanilla, property);
        }
        CustomPart custom = findCustomPart(partName);
        return custom == null ? 0.0D : getCustom(custom, property);
    }

    public double vanillaValue(ModelRenderer part, String property) {
        Map<String, Double> overrides = vanillaOverrides.get(part);
        if (overrides != null && overrides.containsKey(property)) return overrides.get(property);
        return getVanilla(part, property);
    }

    public void applyInheritedBipedPose(ModelRenderer part, float x, float y, float z,
                                        float rx, float ry, float rz, float sx, float sy, float sz) {
        applyVanillaOverride(part, "tx", x);
        applyVanillaOverride(part, "ty", y);
        applyVanillaOverride(part, "tz", z);
        applyVanillaOverride(part, "rx", rx);
        applyVanillaOverride(part, "ry", ry);
        applyVanillaOverride(part, "rz", rz);
        applyVanillaOverride(part, "sx", sx);
        applyVanillaOverride(part, "sy", sy);
        applyVanillaOverride(part, "sz", sz);
    }

    private void applyVanillaOverride(ModelRenderer part, String property, double value) {
        originalPoses.computeIfAbsent(part, VanillaPose::new);
        vanillaOverrides.computeIfAbsent(part, ignored -> new HashMap<>()).put(property, value);
        setVanilla(part, property, value);
    }

    public boolean hasExtendedPose(ModelRenderer part) {
        return vanillaValue(part, "sx") != 1 || vanillaValue(part, "sy") != 1
                || vanillaValue(part, "sz") != 1 || !truth(vanillaValue(part, "visible_boxes"));
    }

    private CustomPart findCustomPart(String name) {
        return model == null ? null : model.findPart(name);
    }

    private ModelRenderer findVanillaPart(String name) {
        // player_cape.jem uses "cape" and "cloak" as IDs for custom nodes,
        // while MITE's native renderer also exposes a part named "cape".
        // Resolve the custom hierarchy first for this feature so animation
        // assignments do not move the native cape pivot (the CEM geometry is
        // already attached to that pivot by CustomModel.render()).
        if (model != null && model.modelName().equals("player_cape") && model.findPart(name) != null) {
            return null;
        }
        if (model != null) return model.findVanillaPart(name);
        if (vanillaParts == null) {
            return null;
        }
        for (Map.Entry<ModelRenderer, String> entry : vanillaParts.entrySet()) {
            if (entry.getValue().equals(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static double getCustom(CustomPart part, String property) {
        return switch (property) {
            case "tx" -> part.x;
            case "ty" -> part.y;
            case "tz" -> part.z;
            case "rx" -> part.rx;
            case "ry" -> part.ry;
            case "rz" -> part.rz;
            case "sx" -> part.sx;
            case "sy" -> part.sy;
            case "sz" -> part.sz;
            case "visible" -> bool(part.visible);
            case "visible_boxes" -> bool(part.visibleBoxes);
            default -> 0.0D;
        };
    }

    private static void setCustom(CustomPart part, String property, double value) {
        float result = (float) value;
        switch (property) {
            case "tx" -> part.x = result;
            case "ty" -> part.y = result;
            case "tz" -> part.z = result;
            case "rx" -> part.rx = result;
            case "ry" -> part.ry = result;
            case "rz" -> part.rz = result;
            case "sx" -> part.sx = result;
            case "sy" -> part.sy = result;
            case "sz" -> part.sz = result;
            case "visible" -> part.visible = truth(value);
            case "visible_boxes" -> part.visibleBoxes = truth(value);
            default -> {
            }
        }
    }

    private static double getVanilla(ModelRenderer part, String property) {
        return switch (property) {
            case "tx" -> part.rotationPointX;
            case "ty" -> part.rotationPointY;
            case "tz" -> part.rotationPointZ;
            case "rx" -> part.rotateAngleX;
            case "ry" -> part.rotateAngleY;
            case "rz" -> part.rotateAngleZ;
            case "visible" -> bool(part.showModel && !part.isHidden);
            case "visible_boxes" -> 1.0D;
            case "sx", "sy", "sz" -> 1.0D;
            default -> 0.0D;
        };
    }

    private static void setVanilla(ModelRenderer part, String property, double value) {
        float result = (float) value;
        switch (property) {
            case "tx" -> part.rotationPointX = result;
            case "ty" -> part.rotationPointY = result;
            case "tz" -> part.rotationPointZ = result;
            case "rx" -> part.rotateAngleX = result;
            case "ry" -> part.rotateAngleY = result;
            case "rz" -> part.rotateAngleZ = result;
            case "visible" -> part.showModel = truth(value);
            default -> {
            }
        }
    }

    private boolean isChild() {
        return entity instanceof EntityAgeable ageable && ageable.isChild() ||
                entity instanceof EntityLiving living && living.isChild();
    }

    private double playerCoordinate(int axis) {
        if (Minecraft.getMinecraft().thePlayer == null) {
            return 0.0D;
        }
        return switch (axis) {
            case 0 -> MotionMath.lerp(Minecraft.getMinecraft().thePlayer.prevPosX, Minecraft.getMinecraft().thePlayer.posX, partialTick());
            case 1 -> MotionMath.lerp(Minecraft.getMinecraft().thePlayer.prevPosY, Minecraft.getMinecraft().thePlayer.posY, partialTick());
            default -> MotionMath.lerp(Minecraft.getMinecraft().thePlayer.prevPosZ, Minecraft.getMinecraft().thePlayer.posZ, partialTick());
        };
    }

    private boolean guiMotionDisabled() {
        return ETFRenderContext.isInGui() && Minecraft.getMinecraft().currentScreen != null;
    }

    private double movement(boolean strafe) {
        if (guiMotionDisabled()) return 0;
        boolean localPlayer = entity == Minecraft.getMinecraft().thePlayer;
        return MotionMath.direction(localPlayer ? entity.motionX : entity.posX - entity.prevPosX,
                localPlayer ? entity.motionZ : entity.posZ - entity.prevPosZ, entity.rotationYaw, strafe);
    }

    private double anger(boolean start) {
        if (!(entity instanceof EntityPigZombie)) return 0;
        int current = ((EntityPigZombieMixin) entity).emf$getAngerLevel();
        int highest = EMFManager.INSTANCE.observeAnger(entity, current);
        return start ? highest : current <= 0 ? 0 : current - partialTick();
    }

    private boolean isInFluid() {
        return entity.worldObj != null && liquidAt((int) Math.floor(entity.posY));
    }

    private boolean liquidAt(int y) {
        Block block = entity.worldObj.getBlock((int) Math.floor(entity.posX), y, (int) Math.floor(entity.posZ));
        return block != null && block.blockMaterial.isLiquid();
    }

    private int fluidDepth(int direction) {
        if (!isInFluid()) return 0;
        int origin = (int) Math.floor(entity.posY);
        int y = origin;
        while (y > 0 && y < 256 && liquidAt(y)) y += direction;
        return Math.abs(y - origin);
    }

    public float partialTickValue() {
        return (float) partialTick();
    }

    private double partialTick() {
        float frameDelta = EMFManager.INSTANCE.getTickDelta();
        // GuiInventory passes 1 to render the mouse-adjusted angles directly;
        // animation phases still need the real frame interpolation fraction.
        return ETFRenderContext.isInGui() ? frameDelta : ETFRenderContext.tickDelta(frameDelta);
    }

    private double distanceToPlayer() {
        Entity player = Minecraft.getMinecraft().renderViewEntity;
        return player == null ? 0 : entity.getDistanceToEntity(player);
    }

    private boolean isFlying() {
        return entity instanceof EntityPlayer player && player.capabilities != null
                && player.capabilities.isFlying;
    }

    private boolean effectiveSprinting() {
        if (entity == null) return false;
        if (entity.isSprinting()) return true;
        if (!(entity instanceof EntityPlayer player) || !isFlying()) return false;
        Minecraft minecraft = Minecraft.getMinecraft();
        // Run toggling is a client-side control in MITE.  Do not report the
        // local key state for remote players, whose animation must use the
        // synchronized Entity sprint flag instead.
        return minecraft != null && isLocalPlayer(player, minecraft.thePlayer)
                && minecraft.playerController != null
                && minecraft.playerController.isRunToggledOn(player);
    }

    private static boolean isLocalPlayer(EntityPlayer player, EntityPlayer localPlayer) {
        return localPlayer != null && (player == localPlayer || player.entityId == localPlayer.entityId);
    }

    private double effectiveLimbSpeed() {
        if (!isFlying() || !effectiveSprinting()) return limbSpeed;
        // MITE's flight movement path does not apply the run-toggle boost to
        // the horizontal delta used by ModelBase.  Match the vanilla sprint
        // flight visual range without allowing the value to exceed the
        // normalized animation input used by Fresh Animations.
        return Math.min(1.0D, limbSpeed * 1.3D);
    }

    private double heightAboveGround() {
        if (entity.worldObj == null) return 0;
        int x = (int) Math.floor(entity.posX);
        int y = (int) Math.floor(entity.posY);
        int z = (int) Math.floor(entity.posZ);
        while (y > 0 && entity.worldObj.isAirBlock(x, y - 1, z)) y--;
        return entity.posY - y;
    }

    private static double bool(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private static boolean truth(double value) {
        return value != 0.0D && !Double.isNaN(value);
    }
}
