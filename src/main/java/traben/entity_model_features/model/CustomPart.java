package traben.entity_model_features.model;

import net.minecraft.Minecraft;
import net.minecraft.ResourceLocation;
import net.minecraft.Tessellator;
import org.lwjgl.opengl.GL11;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_texture_features.features.EmissiveRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class CustomPart {

    public final String id;
    public final String targetPart;
    public final boolean attach;
    private final boolean capturesArmTransform;
    public float x;
    public float y;
    public float z;
    public float rx;
    public float ry;
    public float rz;
    public float sx;
    public float sy;
    public float sz;
    public boolean visible = true;
    public boolean visibleBoxes = true;
    private final float defaultX;
    private final float defaultY;
    private final float defaultZ;
    private final float defaultRx;
    private final float defaultRy;
    private final float defaultRz;
    private final float defaultScale;
    private final List<CustomBox> boxes = new ArrayList<>();
    private final List<CustomPart> children = new ArrayList<>();
    private final ResourceLocation textureOverride;
    private final Map<String, float[]> attachments;

    CustomPart(JemPart data, String targetPart, int[] parentTextureSize, ResourceLocation parentTexture) {
        this(data, targetPart, parentTextureSize, parentTexture, true);
    }

    private CustomPart(JemPart data, String targetPart, int[] parentTextureSize,
                       ResourceLocation parentTexture, boolean capturesArmTransform) {
        this.id = data.id == null || data.id.isEmpty() ? "part_" + Integer.toHexString(System.identityHashCode(data)) : data.id;
        data.id = id;
        this.targetPart = targetPart;
        this.attach = data.attach;
        this.capturesArmTransform = capturesArmTransform;
        int[] textureSize = data.textureSize != null && data.textureSize.length == 2 ? data.textureSize : parentTextureSize;
        textureOverride = data.customTexture == null ? parentTexture : data.customTexture;
        boolean invertX = data.invertAxis.contains("x");
        boolean invertY = data.invertAxis.contains("y");
        boolean invertZ = data.invertAxis.contains("z");
        attachments = new LinkedHashMap<>();
        data.attachments.forEach((name, point) -> {
            if (point != null && point.length == 3) attachments.put(name,
                    new float[]{point[0] * (invertX ? -1 : 1), point[1] * (invertY ? -1 : 1), point[2] * (invertZ ? -1 : 1)});
        });
        float[] translate = data.translate != null && data.translate.length == 3 ? data.translate : new float[3];
        float[] rotate = data.rotate != null && data.rotate.length == 3 ? data.rotate : new float[3];
        x = invertX ? -translate[0] : translate[0];
        y = invertY ? -translate[1] : translate[1];
        z = invertZ ? -translate[2] : translate[2];
        rx = (float) Math.toRadians(invertX ? -rotate[0] : rotate[0]);
        ry = (float) Math.toRadians(invertY ? -rotate[1] : rotate[1]);
        rz = (float) Math.toRadians(invertZ ? -rotate[2] : rotate[2]);
        sx = sy = sz = data.scale;
        defaultX = x;
        defaultY = y;
        defaultZ = z;
        defaultRx = rx;
        defaultRy = ry;
        defaultRz = rz;
        defaultScale = data.scale;
        for (JemBox box : data.boxes) {
            boxes.add(new CustomBox(box, data.invertAxis, data.mirrorTexture,
                    textureSize[0], textureSize[1]));
        }
        if (!data.sprites.isEmpty()) System.err.println("[EMF] Ignoring unsupported sprites in " + id + " (upstream EMF does not implement sprites)");
        if (data.submodel != null) {
            children.add(new CustomPart(data.submodel, targetPart, textureSize, textureOverride, false));
        }
        for (JemPart child : data.submodels) {
            children.add(new CustomPart(child, targetPart, textureSize, textureOverride, false));
        }
    }

    public void render(float scale) {
        render(scale, ETFRenderContext.currentTexture());
    }

    private void render(float scale, ResourceLocation inheritedTexture) {
        if (!visible) {
            return;
        }
        ResourceLocation activeTexture = selectedTexture(inheritedTexture);
        bindIfChanged(inheritedTexture, activeTexture);
        GL11.glPushMatrix();
        try {
            if (capturesArmTransform) {
                // The part's authored translation places its cuboid geometry at the
                // arm pivot (for FA this is roughly -5,-22,0).  It is not an item
                // offset.  Record only the animated delta from that authored pose,
                // while retaining the arm's CEM rotation and scale.
                RenderContext.recordPlayerArmTransform(targetPart, scale,
                        x - defaultX, y - defaultY, z - defaultZ,
                        rx - defaultRx, ry - defaultRy, rz - defaultRz,
                        defaultScale == 0.0F ? sx : sx / defaultScale,
                        defaultScale == 0.0F ? sy : sy / defaultScale,
                        defaultScale == 0.0F ? sz : sz / defaultScale);
            }
            applyTransform(scale);
            renderContents(scale, activeTexture);
        } finally {
            GL11.glPopMatrix();
            bindIfChanged(activeTexture, inheritedTexture);
        }
    }

    void applyTransform(float scale) {
        GL11.glTranslatef(x * scale, y * scale, z * scale);
        if (rz != 0.0F) GL11.glRotatef((float) Math.toDegrees(rz), 0.0F, 0.0F, 1.0F);
        if (ry != 0.0F) GL11.glRotatef((float) Math.toDegrees(ry), 0.0F, 1.0F, 0.0F);
        if (rx != 0.0F) GL11.glRotatef((float) Math.toDegrees(rx), 1.0F, 0.0F, 0.0F);
        if (sx != 1.0F || sy != 1.0F || sz != 1.0F) GL11.glScalef(sx, sy, sz);
    }


    void renderContents(float scale) {
        ResourceLocation inheritedTexture = ETFRenderContext.currentTexture();
        ResourceLocation activeTexture = selectedTexture(inheritedTexture);
        bindIfChanged(inheritedTexture, activeTexture);
        renderContents(scale, activeTexture);
        bindIfChanged(activeTexture, inheritedTexture);
    }

    private void renderContents(float scale, ResourceLocation activeTexture) {
        attachments.forEach((name, point) -> {
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(point[0] * scale, point[1] * scale, point[2] * scale);
                RenderContext.recordAttachment(name);
            } finally { GL11.glPopMatrix(); }
        });
        if (visibleBoxes) {
            Tessellator tessellator = Tessellator.instance;
            for (CustomBox box : boxes) {
                DebugGeometry.draw(() -> EmissiveRenderer.draw(() -> box.render(tessellator, scale)), true);
            }
        }
        for (CustomPart child : children) {
            child.render(scale, activeTexture);
        }
    }

    private ResourceLocation selectedTexture(ResourceLocation inheritedTexture) {
        if (textureOverride == null) {
            return inheritedTexture;
        }
        return ETFApi.getVariantTexture(ETFRenderContext.currentEntity(), textureOverride);
    }

    private static void bindIfChanged(ResourceLocation current, ResourceLocation requested) {
        if (requested != null && !requested.equals(current)) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(requested);
            ETFRenderContext.setCurrentTexture(requested);
        }
    }

    public void collect(Map<String, CustomPart> parts) {
        parts.putIfAbsent(id, this);
        for (CustomPart child : children) {
            child.collect(parts);
        }
    }

    void collectAll(List<CustomPart> parts) {
        parts.add(this);
        for (CustomPart child : children) child.collectAll(parts);
    }

    public CustomPart descendant(String name) {
        for (CustomPart child : children) {
            if (child.id.equals(name)) return child;
            CustomPart result = child.descendant(name);
            if (result != null) return result;
        }
        return null;
    }

    boolean applyAttachment(String name, float scale) {
        float[] point = attachments.get(name);
        if (point != null && point.length == 3) {
            applyTransform(scale);
            GL11.glTranslatef(point[0] * scale, point[1] * scale, point[2] * scale);
            return true;
        }
        for (CustomPart child : children) {
            if (child.hasAttachment(name)) {
                applyTransform(scale);
                return child.applyAttachment(name, scale);
            }
        }
        return false;
    }

    boolean hasAttachment(String name) {
        if (attachments.containsKey(name)) return true;
        for (CustomPart child : children) if (child.hasAttachment(name)) return true;
        return false;
    }

    void resetPose() {
        x = defaultX;
        y = defaultY;
        z = defaultZ;
        rx = defaultRx;
        ry = defaultRy;
        rz = defaultRz;
        sx = sy = sz = defaultScale;
        visible = true;
        visibleBoxes = true;
        for (CustomPart child : children) {
            child.resetPose();
        }
    }
}
