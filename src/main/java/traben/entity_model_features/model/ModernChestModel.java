package traben.entity_model_features.model;

import net.minecraft.ModelChest;
import net.minecraft.ModelRenderer;

/** Modern chest-local coordinates, before the renderer's legacy Y/Z conversion. */
public final class ModernChestModel extends ModelChest {

    public final boolean left;
    public final boolean doubleChest;

    public ModernChestModel() {
        this(false, false);
    }

    public ModernChestModel(boolean left) {
        this(left, true);
    }

    private ModernChestModel(boolean left, boolean doubleChest) {
        this.left = left;
        this.doubleChest = doubleChest;
        boxList.clear();
        textureWidth = textureHeight = 64;
        float x = left ? 0 : 1;
        int width = doubleChest ? 15 : 14;
        chestBelow = box(0, 19, x, 0, 1, width, 10, 14, 0, 0);
        chestLid = box(0, 0, x, 0, 0, width, 5, 14, 9, 1);
        chestKnob = box(0, 0, doubleChest ? left ? 0 : 15 : 7, -2, 14, doubleChest ? 1 : 2, 4, 1, 9, 1);
    }

    private ModelRenderer box(int u, int v, float x, float y, float z, int w, int h, int d,
                              float py, float pz) {
        ModelRenderer part = new ModelRenderer(this, u, v);
        part.addBox(x, y, z, w, h, d);
        part.setRotationPoint(0, py, pz);
        return part;
    }
}
