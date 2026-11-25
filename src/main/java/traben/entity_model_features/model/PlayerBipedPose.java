package traben.entity_model_features.model;

import net.minecraft.ModelBase;
import net.minecraft.ModelBiped;
import net.minecraft.ModelRenderer;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.animation.RenderParameters;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pose shared from the animated player model with its armor render passes. */
public final class PlayerBipedPose {

    private static final String[] PART_NAMES = {
            "head", "headwear", "body", "left_arm", "right_arm", "left_leg", "right_leg"
    };

    private final Map<String, PartPose> parts;
    private final RootPose root;

    private PlayerBipedPose(Map<String, PartPose> parts, RootPose root) {
        this.parts = parts;
        this.root = root;
    }

    public static PlayerBipedPose capture(ModelBase base, CustomModel model, RenderParameters parameters) {
        if (!(base instanceof ModelBiped)) return null;
        Map<String, PartPose> parts = new LinkedHashMap<>();
        for (String name : PART_NAMES) {
            ModelRenderer part = model == null
                    ? vanillaPart((ModelBiped) base, name)
                    : model.findVanillaPart(name);
            if (part != null) parts.put(name, PartPose.capture(part, parameters));
        }
        CustomPart root = model == null ? null : model.rootPart();
        return new PlayerBipedPose(parts, root == null ? null : RootPose.capture(root));
    }

    public boolean applyTo(ModelBase base, CustomModel model, RenderParameters parameters) {
        if (!(base instanceof ModelBiped) || parameters == null || model != null && model.hasAnimatedRoot()) {
            return false;
        }
        for (Map.Entry<String, PartPose> entry : parts.entrySet()) {
            ModelRenderer target = model == null
                    ? vanillaPart((ModelBiped) base, entry.getKey())
                    : model.findVanillaPart(entry.getKey());
            if (target != null) entry.getValue().apply(target, parameters);
        }
        return true;
    }

    public RootPose rootPose() {
        return root;
    }

    private static ModelRenderer vanillaPart(ModelBiped model, String name) {
        return switch (name) {
            case "head" -> model.bipedHead;
            case "headwear" -> model.bipedHeadwear;
            case "body" -> model.bipedBody;
            case "left_arm" -> model.bipedLeftArm;
            case "right_arm" -> model.bipedRightArm;
            case "left_leg" -> model.bipedLeftLeg;
            case "right_leg" -> model.bipedRightLeg;
            default -> null;
        };
    }

    private record PartPose(float x, float y, float z, float rx, float ry, float rz,
                            float sx, float sy, float sz) {

        static PartPose capture(ModelRenderer part, RenderParameters parameters) {
            return new PartPose(part.rotationPointX, part.rotationPointY, part.rotationPointZ,
                    part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ,
                    parameters == null ? 1.0F : (float) parameters.vanillaValue(part, "sx"),
                    parameters == null ? 1.0F : (float) parameters.vanillaValue(part, "sy"),
                    parameters == null ? 1.0F : (float) parameters.vanillaValue(part, "sz"));
        }

        void apply(ModelRenderer part, RenderParameters parameters) {
            parameters.applyInheritedBipedPose(part, x, y, z, rx, ry, rz, sx, sy, sz);
        }
    }

    public record RootPose(float x, float y, float z, float rx, float ry, float rz,
                           float sx, float sy, float sz) {

        private static RootPose capture(CustomPart part) {
            return new RootPose(part.x, part.y, part.z, part.rx, part.ry, part.rz,
                    part.sx, part.sy, part.sz);
        }

        public void apply(float scale) {
            GL11.glTranslatef(x * scale, y * scale, z * scale);
            if (rz != 0.0F) GL11.glRotatef((float) Math.toDegrees(rz), 0, 0, 1);
            if (ry != 0.0F) GL11.glRotatef((float) Math.toDegrees(ry), 0, 1, 0);
            if (rx != 0.0F) GL11.glRotatef((float) Math.toDegrees(rx), 1, 0, 0);
            if (sx != 1.0F || sy != 1.0F || sz != 1.0F) GL11.glScalef(sx, sy, sz);
        }
    }
}
