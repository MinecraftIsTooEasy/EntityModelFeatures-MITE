package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.EntityLivingBase;
import net.minecraft.ModelBase;
import net.minecraft.RendererLivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.model.RenderContext;

@Mixin(RendererLivingEntity.class)
public abstract class RendererLivingEntityMixin {

    @Shadow
    protected ModelBase mainModel;

    @Inject(method = "doRenderLiving(Lnet/minecraft/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void beginCustomEntityRender(EntityLivingBase entity, double x, double y, double z,
                                         float yaw, float tickDelta, CallbackInfo callbackInfo) {
        RenderContext.beginEntity(entity);
    }

    @Inject(method = "doRenderLiving(Lnet/minecraft/EntityLivingBase;DDDFF)V", at = @At("RETURN"))
    private void endCustomEntityRender(EntityLivingBase entity, double x, double y, double z,
                                       float yaw, float tickDelta, CallbackInfo callbackInfo) {
        RenderContext.endEntity();
    }

    @WrapOperation(method = {"renderModel", "renderModelGlowing"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V"))
    private void wrapModelRender(ModelBase model, Entity entity, float limbSwing, float limbSpeed,
                                 float age, float headYaw, float headPitch, float scale,
                                 Operation<Void> original) {
        limbSwing = RenderContext.guiPreviewLimbSwing(entity, limbSwing);
        limbSpeed = RenderContext.guiPreviewLimbSpeed(entity, limbSpeed);
        age = RenderContext.guiPreviewAge(entity, age);
        RenderContext.begin(this, model, entity, false, limbSwing, limbSpeed, age,
                headYaw, headPitch, scale);
        try {
            original.call(model, entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        } finally {
            RenderContext.end();
        }
    }

    @WrapOperation(method = "doRenderLiving(Lnet/minecraft/EntityLivingBase;DDDFF)V", at = { @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 3), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 4), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 6)})
    private void renderMainOverlay(ModelBase model, Entity entity, float limbSwing, float limbSpeed,
                                   float age, float headYaw, float headPitch, float scale, Operation<Void> original) {
        wrapModelRender(model, entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale, original);
    }

    @WrapOperation(method = "doRenderLiving(Lnet/minecraft/EntityLivingBase;DDDFF)V", at = { @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 0), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 1), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 2), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 5), @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V", ordinal = 7)})
    private void renderFeatureModel(ModelBase model, Entity entity, float limbSwing, float limbSpeed,
                                     float age, float headYaw, float headPitch, float scale, Operation<Void> original) {
        limbSwing = RenderContext.guiPreviewLimbSwing(entity, limbSwing);
        limbSpeed = RenderContext.guiPreviewLimbSpeed(entity, limbSpeed);
        age = RenderContext.guiPreviewAge(entity, age);
        RenderContext.begin(this, model, entity, true, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        try { original.call(model, entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale); }
        finally { RenderContext.end(); }
    }

    @WrapOperation(method = "doRenderLiving(Lnet/minecraft/EntityLivingBase;DDDFF)V", at = { @At(value = "INVOKE", target = "Lnet/minecraft/RendererLivingEntity;shouldRenderPass(Lnet/minecraft/EntityLivingBase;IF)I"), @At(value = "INVOKE", target = "Lnet/minecraft/RendererLivingEntity;inheritRenderPass(Lnet/minecraft/EntityLivingBase;IF)I")})
    private int selectFeaturePass(RendererLivingEntity renderer, EntityLivingBase entity, int index,
                                  float tickDelta, Operation<Integer> original) {
        RenderContext.setPassIndex(index);
        return original.call(renderer, entity, index, tickDelta);
    }
}
