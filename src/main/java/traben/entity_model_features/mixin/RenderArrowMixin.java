package traben.entity_model_features.mixin;

import net.minecraft.EntityArrow;
import net.minecraft.Minecraft;
import net.minecraft.ModelBase;
import net.minecraft.RenderArrow;
import net.minecraft.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.model.ModelArrow;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.features.ETFManager;
import traben.entity_texture_features.features.ETFRenderContext;

/** Bridges old arrow CEM models into MITE's otherwise tessellator-only arrow renderer. */
@Mixin(RenderArrow.class)
public abstract class RenderArrowMixin {

    @Shadow
    protected abstract ResourceLocation getArrowTextures(EntityArrow entity);

    private static final ModelArrow EMF_ARROW_MODEL = new ModelArrow();

    @Inject(method = "renderArrow(Lnet/minecraft/EntityArrow;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void renderCustomArrow(EntityArrow entity, double x, double y, double z,
                                   float yaw, float delta, CallbackInfo callbackInfo) {
        if (!EMFManager.INSTANCE.hasModel("arrow", entity)) return;
        if (EMFManager.INSTANCE.getModel(this, EMF_ARROW_MODEL, entity, false) == null) return;

        ResourceLocation original = getArrowTextures(entity);
        ResourceLocation texture = ETFManager.INSTANCE.getVariantTexture(entity, original);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        ETFRenderContext.setCurrentTexture(texture);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef((float) x, (float) y, (float) z);
            GL11.glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * delta - 90.0F,
                    0.0F, 1.0F, 0.0F);
            GL11.glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * delta,
                    0.0F, 0.0F, 1.0F);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            float shake = entity.arrowShake - delta;
            if (shake > 0.0F) {
                float angle = (float) (-Math.sin(shake * 3.0F) * shake);
                GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
            }
            GL11.glRotatef(45.0F, 1.0F, 0.0F, 0.0F);
            GL11.glScalef(0.05625F, 0.05625F, 0.05625F);
            GL11.glTranslatef(-4.0F, 0.0F, 0.0F);
            RenderContext.begin(this, EMF_ARROW_MODEL, entity, false, 0, 0, entity.ticksExisted,
                    entity.rotationYaw, entity.rotationPitch, 0.0625F);
            try {
                EMF_ARROW_MODEL.render(entity, 0, 0, entity.ticksExisted, entity.rotationYaw,
                        entity.rotationPitch, 0.0625F);
            } finally {
                RenderContext.end();
            }
        } finally {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glPopMatrix();
        }
        callbackInfo.cancel();
    }
}
