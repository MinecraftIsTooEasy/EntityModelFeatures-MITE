package traben.entity_model_features.model;

import net.minecraft.ModelBase;
import net.minecraft.ModelHorse;
import net.minecraft.ModelRenderer;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.animation.AnimationProgram;
import traben.entity_model_features.animation.RenderParameters;
import traben.entity_model_features.models.animation.AnimSetupContext;
import traben.entity_texture_features.features.ETFRenderContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import traben.entity_texture_features.features.EmissiveRenderer;

public final class CustomModel {

    private final Map<ModelRenderer, String> vanillaParts;
    private final ModelBase sourceModel;
    private final String modelName;
    private final ModelBase drawingModel;
    private final ModelStructure structure;
    private final BabyModelPose babyPose;
    private final int textureWidth;
    private final int textureHeight;
    private final ResourceLocation customTexture;
    private final Map<String, List<CustomPart>> partsByTarget = new HashMap<>();
    private final Map<String, CustomPart> partsById = new HashMap<>();
    private final List<CustomPart> rootParts = new ArrayList<>();
    private final List<CustomPart> allParts = new ArrayList<>();
    private final List<AnimationProgram.Assignment> animationAssignments = new ArrayList<>();
    private final Map<ModelRenderer, VanillaPose> vanillaDefaults = new IdentityHashMap<>();
    private final float shadowSize;
    private int variant;
    private AnimSetupContext compileContext;

    public CustomModel(JemModel data, ModelBase vanillaModel) {
        this(data, vanillaModel, vanillaModel.getClass().getSimpleName());
    }

    public CustomModel(JemModel data, ModelBase vanillaModel, String modelName) {
        this.modelName = modelName;
        sourceModel = vanillaModel;
        drawingModel = vanillaModel instanceof ModelHorse
                ? new ModernHorseModel(modelName.startsWith("donkey") || modelName.startsWith("mule"),
                modelName.equals("horse_armor"), modelName.endsWith("_saddle"), vanillaModel) : vanillaModel;
        structure = new ModelStructure(drawingModel, ModelPartResolver.resolve(drawingModel));
        babyPose = BabyModelPose.forModel(drawingModel);
        int[] textureSize = validTextureSize(data.textureSize);
        textureWidth = textureSize[0];
        textureHeight = textureSize[1];
        customTexture = data.customTexture;
        shadowSize = data.shadowSize == 1.0F ? Float.NaN : Math.max(0.0F, data.shadowSize);
        vanillaParts = structure.parts();
        vanillaParts.keySet().forEach(part -> vanillaDefaults.put(part, new VanillaPose(part)));
        for (JemPart part : data.models) {
            if (part.part == null || part.part.isEmpty()) {
                continue;
            }
            String target = structure.canonicalName(part.part.equals("cloak") ? "cape" : part.part);
            CustomPart customPart = new CustomPart(part, target, textureSize, data.customTexture);
            rootParts.add(customPart);
            partsByTarget.computeIfAbsent(target, ignored -> new ArrayList<>()).add(customPart);
            customPart.collect(partsById);
            customPart.collectAll(allParts);
        }
        HashMap<String, Object> parts = new HashMap<>();
        vanillaParts.forEach((part, name) -> parts.put(name, part));
        structure.names().forEach(name -> parts.put(name, structure.find(name)));
        partsById.forEach(parts::put);
        for (JemPart part : data.models) collectPartPaths(part, "", parts);
        try (AnimSetupContext context = new AnimSetupContext(modelName, this, parts)) {
            compileContext = context;
            for (JemPart part : data.models) collectAnimations(part, "", part.part);
        } finally { compileContext = null; }
    }

    private void collectPartPaths(JemPart part, String parent, Map<String, Object> parts) {
        String path = parent.isEmpty() ? part.id : parent + ":" + part.id;
        CustomPart custom = partsById.get(part.id);
        if (custom != null) parts.put(path, custom);
        if (part.submodel != null) collectPartPaths(part.submodel, path, parts);
        for (JemPart child : part.submodels) collectPartPaths(child, path, parts);
    }

    public int variant() { return variant; }
    public String modelName() { return modelName; }
    public void setVariant(int variant) { this.variant = variant; }
    public boolean isAnimated() { return !animationAssignments.isEmpty(); }
    public boolean isPartCustom(Object part) { return allParts.contains(part); }
    public boolean isPartAnimated(Object part) {
        return animationAssignments.stream().anyMatch(assignment -> targetPart(assignment.target()) == part);
    }
    public Object targetPart(String target) {
        int dot = target.lastIndexOf('.');
        if (dot < 0) return null;
        String name = target.substring(0, dot);
        ModelRenderer vanilla = findVanillaPart(name);
        if (vanilla != null) return vanilla;
        return findPart(name);
    }

    public ModelBase drawingModel() { return drawingModel; }

    public int textureWidth() { return textureWidth; }

    public int textureHeight() { return textureHeight; }

    public ResourceLocation customTexture() { return customTexture; }

    public boolean hasBakedBabyPose() { return babyPose != null; }

    public void applyBabyPose(RenderParameters parameters) {
        if (babyPose != null) babyPose.apply(structure, parameters);
    }

    boolean bridges(ModelBase model) { return sourceModel == model && drawingModel != model; }

    public ModelRenderer findVanillaPart(String name) { return structure.find(name); }

    CustomPart rootPart() {
        List<CustomPart> roots = partsByTarget.get("root");
        return roots == null || roots.isEmpty() ? null : roots.get(0);
    }

    boolean hasAnimatedRoot() {
        CustomPart root = rootPart();
        return root != null && isPartAnimated(root);
    }

    public CustomPart findPart(String path) {
        String[] segments = path.split(":");
        CustomPart part = partsById.get(segments[0]);
        if (part == null) {
            List<CustomPart> targets = partsByTarget.get(structure.canonicalName(segments[0]));
            if (targets != null && !targets.isEmpty()) {
                if (segments.length == 1) return null; // bare vanilla names address the vanilla parent
                for (CustomPart target : targets) {
                    CustomPart result = target.id.equals(segments[1]) ? target : target.descendant(segments[1]);
                    if (result != null) { part = result; break; }
                }
                if (part == null) return null;
                for (int i = 2; i < segments.length && part != null; i++) part = part.descendant(segments[i]);
                return part;
            }
        }
        for (int i = 1; i < segments.length && part != null; i++) part = part.descendant(segments[i]);
        return part;
    }

    public float shadowSize() {
        return shadowSize;
    }

    public void resetPose() {
        for (CustomPart part : rootParts) {
            part.resetPose();
        }
    }

    public void resetVanillaPartsToDefaults() {
        resetVanillaPartsToDefaults(false);
    }

    public void resetVanillaPartsToDefaults(boolean preserveVisibility) {
        vanillaDefaults.forEach((part, pose) -> pose.restore(part, preserveVisibility));
    }

    public void animate(RenderParameters parameters) {
        parameters.animate(this, animationAssignments, allParts, vanillaParts);
    }

    RenderStart beginRender(float scale, RenderParameters parameters, PlayerBipedPose.RootPose inheritedRoot) {
        CustomPart root = rootPart();
        if (root == null && inheritedRoot == null) {
            return new RenderStart(true, false);
        }
        if (root != null && !root.visible) {
            return new RenderStart(false, false);
        }
        GL11.glPushMatrix();
        if (inheritedRoot != null) inheritedRoot.apply(scale);
        else root.applyTransform(scale);
        // First-person hand callbacks render one vanilla limb, not the full player hierarchy.
        if (root != null && !ETFRenderContext.isFirstPersonHand() && parameters.markRootRendered()) {
            root.renderContents(scale);
        }
        return new RenderStart(true, true);
    }

    void endRender(boolean rootTransformPushed) {
        if (rootTransformPushed) {
            GL11.glPopMatrix();
        }
    }

    record RenderStart(boolean render, boolean rootTransformPushed) {
    }

    private record VanillaPose(float x, float y, float z, float rx, float ry, float rz,
                               boolean show, boolean hidden) {

        private VanillaPose(ModelRenderer part) {
            this(part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                    part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                    part.showModel, part.isHidden);
        }

        private void restore(ModelRenderer part, boolean preserveVisibility) {
            part.rotationPointX = x;
            part.rotationPointY = y;
            part.rotationPointZ = z;
            part.rotateAngleX = rx;
            part.rotateAngleY = ry;
            part.rotateAngleZ = rz;
            if (!preserveVisibility) {
                part.showModel = show;
                part.isHidden = hidden;
            }
        }
    }

    boolean handles(ModelRenderer vanillaPart) {
        return partsByTarget.containsKey(vanillaParts.get(vanillaPart))
                || structure.hasChildren(vanillaPart) || structure.hasSplitBoxes(vanillaPart)
                || structure.isVirtual(vanillaPart);
    }

    boolean postRender(ModelRenderer vanillaPart, float scale) {
        if (!vanillaParts.containsKey(vanillaPart)) return false;
        List<CustomPart> roots = partsByTarget.get("root");
        if (roots != null && !roots.isEmpty()) {
            roots.get(0).applyTransform(scale);
        }
        applyVanillaTransform(vanillaPart, scale, false, false);
        return true;
    }

    void render(ModelRenderer vanillaPart, float scale, RenderParameters parameters) {
        render(vanillaPart, scale, parameters, false);
    }

    void render(ModelRenderer vanillaPart, float scale, RenderParameters parameters, boolean rotated) {
        String name = vanillaParts.get(vanillaPart);
        List<CustomPart> parts = partsByTarget.get(name);
        if (parts == null || parts.isEmpty()) {
            renderNative(vanillaPart, scale, parameters, rotated);
            return;
        }
        if (vanillaPart.isHidden || !vanillaPart.showModel) {
            return;
        }
        animate(parameters);
        boolean replace = parts.stream().anyMatch(part -> !part.attach);
        if (replace && !parameters.markRendered(name, vanillaPart)) {
            if (!rotated) renderVanillaChildren(vanillaPart, scale);
            return;
        }
        if (!replace) {
            renderNative(vanillaPart, scale, parameters, rotated);
        }
        if (replace) {
            GL11.glPushMatrix();
            applyVanillaTransform(vanillaPart, scale, !rotated, rotated);
            RenderContext.applyExternalPartTransformHooks(
                    drawingModel, RenderContext.currentEntity(), vanillaPart, scale, parameters.partialTickValue());
            try {
                RenderContext.recordPlayerArmTransform(drawingModel, RenderContext.currentEntity(), vanillaPart);
                if (!rotated) renderSpecifiedVanillaChildren(vanillaPart, scale);
                for (CustomPart part : parts) {
                    if (!part.attach) {
                        part.render(scale);
                    }
                }
            } finally {
                GL11.glPopMatrix();
            }
        }
        if (parts.stream().anyMatch(part -> part.attach)) {
            GL11.glPushMatrix();
            applyVanillaTransform(vanillaPart, scale, !rotated, rotated);
            RenderContext.applyExternalPartTransformHooks(
                    drawingModel, RenderContext.currentEntity(), vanillaPart, scale, parameters.partialTickValue());
            try {
                for (CustomPart part : parts) {
                    if (part.attach) {
                        part.render(scale);
                    }
                }
            } finally {
                GL11.glPopMatrix();
            }
        }
    }


    private void renderNative(ModelRenderer part, float scale, RenderParameters parameters, boolean rotated) {
        if (!structure.hasChildren(part) && !structure.hasSplitBoxes(part) && !structure.isVirtual(part)) {
            RenderContext.bypassOnce(part);
            RenderContext.renderVanilla(part, scale, rotated);
            return;
        }
        if (part.isHidden || !part.showModel) return;
        GL11.glPushMatrix();
        try {
            applyVanillaTransform(part, scale, !rotated, rotated);
            RenderContext.applyExternalPartTransformHooks(
                    drawingModel, RenderContext.currentEntity(), part, scale, parameters.partialTickValue());
            try {
                RenderContext.recordPlayerArmTransform(drawingModel, RenderContext.currentEntity(), part);
                if (parameters.vanillaValue(part, "visible_boxes") != 0)
                    DebugGeometry.draw(() -> EmissiveRenderer.draw(() -> structure.boxes(part)
                            .forEach(box -> box.render(Tessellator.instance, scale))), false);
                if (!rotated) for (ModelRenderer child : structure.allChildren(part)) child.render(scale);
            } finally {
            }
        } finally { GL11.glPopMatrix(); }
    }

    private void renderSpecifiedVanillaChildren(ModelRenderer part, float scale) {
        for (ModelRenderer renderer : structure.allChildren(part)) {
            if (partsByTarget.containsKey(vanillaParts.get(renderer))) renderer.render(scale);
        }
    }

    boolean applyAttachment(String name, float scale) {
        for (CustomPart part : rootParts) {
            if (!part.hasAttachment(name)) continue;
            List<CustomPart> roots = partsByTarget.get("root");
            if (!part.targetPart.equals("root") && roots != null && !roots.isEmpty()) roots.get(0).applyTransform(scale);
            if (!part.targetPart.equals("root")) {
                for (var vanilla : vanillaParts.entrySet()) {
                    if (part.targetPart.equals(vanilla.getValue())) {
                        List<ModelRenderer> path = pathToPart(vanilla.getKey());
                        for (ModelRenderer ancestor : path) {
                            applyVanillaTransform(ancestor, scale, false, false);
                            // Keep the tail transform in the attachment's
                            // matrix so held items follow the animated arm.
                            RenderContext.applyExternalPartTransformHooks(
                                    drawingModel, RenderContext.currentEntity(), ancestor, scale,
                                    RenderContext.currentPartialTick());
                        }
                        break;
                    }
                }
            }
            return part.applyAttachment(name, scale);
        }
        return false;
    }

    private List<ModelRenderer> pathToPart(ModelRenderer target) {
        List<ModelRenderer> longest = List.of(target);
        for (ModelRenderer candidate : vanillaParts.keySet()) {
            List<ModelRenderer> path = new ArrayList<>();
            if (findPath(candidate, target, path) && path.size() > longest.size()) longest = path;
        }
        return longest;
    }

    private boolean findPath(ModelRenderer part, ModelRenderer target, List<ModelRenderer> path) {
        path.add(part);
        if (part == target) return true;
        for (ModelRenderer child : structure.allChildren(part))
            if (findPath(child, target, path)) return true;
        path.remove(path.size() - 1);
        return false;
    }

    private void collectAnimations(JemPart part, String parent, String vanillaTarget) {
        String id = part.id == null || part.id.isEmpty() ? vanillaTarget : part.id;
        String owner = parent.isEmpty() ? id : parent + ":" + id;
        for (LinkedHashMap<String, String> group : part.animations) {
            for (Map.Entry<String, String> animation : group.entrySet()) {
                String target = replaceAliases(animation.getKey(), owner, vanillaTarget);
                String expression = replaceAliases(animation.getValue(), owner, vanillaTarget);
                animationAssignments.add(AnimationProgram.compile(target, expression, compileContext));
            }
        }
        if (part.submodel != null) {
            collectAnimations(part.submodel, owner, vanillaTarget);
        }
        for (JemPart child : part.submodels) {
            collectAnimations(child, owner, vanillaTarget);
        }
    }

    private static String replaceAliases(String value, String owner, String vanillaPart) {
        if (value == null) {
            return "";
        }
        String result = value.replaceAll("(?<![A-Za-z0-9_.])this(?=[^A-Za-z0-9_]|$)", Matcher.quoteReplacement(owner == null ? "" : owner));
        return result.replaceAll("(?<![A-Za-z0-9_.])part(?=[^A-Za-z0-9_]|$)", Matcher.quoteReplacement(vanillaPart == null ? "" : vanillaPart));
    }

    private static int[] validTextureSize(int[] size) {
        return size != null && size.length == 2 ? size : new int[]{64, 32};
    }

    private void renderVanillaChildren(ModelRenderer part, float scale) {
        if (structure.allChildren(part).isEmpty()) {
            return;
        }
        GL11.glPushMatrix();
        try {
            applyVanillaTransform(part, scale);
            for (ModelRenderer child : structure.allChildren(part)) {
                child.render(scale);
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void applyVanillaTransform(ModelRenderer part, float scale) {
        applyVanillaTransform(part, scale, true, false);
    }

    static void applyVanillaTransform(ModelRenderer part, float scale, boolean offsets, boolean rotatedRender) {
        if (offsets) GL11.glTranslatef(part.offsetX, part.offsetY, part.offsetZ);
        GL11.glTranslatef(part.rotationPointX * scale, part.rotationPointY * scale, part.rotationPointZ * scale);
        if (rotatedRender) {
            if (part.rotateAngleY != 0) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleY), 0, 1, 0);
            if (part.rotateAngleX != 0) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleX), 1, 0, 0);
            if (part.rotateAngleZ != 0) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleZ), 0, 0, 1);
            RenderContext.applyVanillaScale(part);
            return;
        }
        if (part.rotateAngleZ != 0.0F) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleZ), 0.0F, 0.0F, 1.0F);
        if (part.rotateAngleY != 0.0F) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleY), 0.0F, 1.0F, 0.0F);
        if (part.rotateAngleX != 0.0F) GL11.glRotatef((float) Math.toDegrees(part.rotateAngleX), 1.0F, 0.0F, 0.0F);
        RenderContext.applyVanillaScale(part);
    }
}
