package traben.entity_model_features.compat;

import net.minecraft.ModelRenderer;

/** A complete legacy ModelRenderer pose captured before EMF evaluates CEM. */
public final class PoseSnapshot {

    private final float rotationPointX;
    private final float rotationPointY;
    private final float rotationPointZ;
    private final float rotateAngleX;
    private final float rotateAngleY;
    private final float rotateAngleZ;
    private final boolean showModel;
    private final boolean hidden;

    public PoseSnapshot(ModelRenderer part) {
        rotationPointX = part.rotationPointX;
        rotationPointY = part.rotationPointY;
        rotationPointZ = part.rotationPointZ;
        rotateAngleX = part.rotateAngleX;
        rotateAngleY = part.rotateAngleY;
        rotateAngleZ = part.rotateAngleZ;
        showModel = part.showModel;
        hidden = part.isHidden;
    }

    public void apply(ModelRenderer part) {
        part.rotationPointX = rotationPointX;
        part.rotationPointY = rotationPointY;
        part.rotationPointZ = rotationPointZ;
        part.rotateAngleX = rotateAngleX;
        part.rotateAngleY = rotateAngleY;
        part.rotateAngleZ = rotateAngleZ;
        part.showModel = showModel;
        part.isHidden = hidden;
    }

    /** Keep CEM's shoulder pivot, scale and visibility when restoring an action rotation. */
    public void applyRotation(ModelRenderer part) {
        part.rotateAngleX = rotateAngleX;
        part.rotateAngleY = rotateAngleY;
        part.rotateAngleZ = rotateAngleZ;
    }
}
