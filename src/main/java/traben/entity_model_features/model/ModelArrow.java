package traben.entity_model_features.model;

import net.minecraft.Entity;
import net.minecraft.ModelBase;
import net.minecraft.ModelRenderer;

/**
 * A tiny ModelBase bridge for the legacy RenderArrow tessellator.
 * OptiFine's old arrow CEM contract names its root {@code body}; the
 * modern EMF mapping also accepts {@code cross_1} and {@code cross_2}.
 */
public final class ModelArrow extends ModelBase {

    public final ModelRenderer body;
    public final ModelRenderer cross_1;
    public final ModelRenderer cross_2;

    public ModelArrow() {
        textureWidth = 32;
        textureHeight = 32;
        body = new ModelRenderer(this, "body");
        cross_1 = new ModelRenderer(this, "cross_1");
        cross_2 = new ModelRenderer(this, "cross_2");
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSpeed, float age,
                       float headYaw, float headPitch, float scale) {
        body.render(scale);
        cross_1.render(scale);
        cross_2.render(scale);
    }
}
