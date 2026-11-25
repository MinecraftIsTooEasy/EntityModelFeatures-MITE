package traben.entity_model_features.mixin;

import net.minecraft.Entity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Render;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.EMFManager;

@Mixin(Render.class)
public abstract class RenderMixin {

    @ModifyExpressionValue(method = {"doRenderShadowAndFire", "renderShadow"}, at = @At(value = "FIELD", target = "Lnet/minecraft/Render;shadowSize:F"))
    private float customShadowSize(float original, Entity entity) {
        float size = EMFManager.INSTANCE.getShadowSize(entity);
        return Float.isNaN(size) ? original : size;
    }

    @ModifyExpressionValue(method = "doRenderShadowAndFire", at = @At(value = "FIELD", target = "Lnet/minecraft/Render;shadowOpaque:F"))
    private float customShadowOpacity(float original, Entity entity) {
        return (float) EMFManager.INSTANCE.getRenderVariable(entity, "render.shadow_opacity", original);
    }

    @WrapOperation(method = "doRenderShadowAndFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/Render;renderShadow(Lnet/minecraft/Entity;DDDFF)V"))
    private void moveShadow(Render renderer, Entity entity, double x, double y, double z,
                            float opacity, float delta, Operation<Void> original) {
        original.call(renderer, entity, x + EMFManager.INSTANCE.getRenderVariable(entity, "render.shadow_offset_x"),
                y, z + EMFManager.INSTANCE.getRenderVariable(entity, "render.shadow_offset_z"), opacity, delta);
    }

    @ModifyExpressionValue(method = "renderShadow", at = { @At(value = "FIELD", target = "Lnet/minecraft/Entity;lastTickPosX:D"), @At(value = "FIELD", target = "Lnet/minecraft/Entity;posX:D")})
    private double shadowSampleX(double original, Entity entity) {
        return original + EMFManager.INSTANCE.getRenderVariable(entity, "render.shadow_offset_x");
    }

    @ModifyExpressionValue(method = "renderShadow", at = { @At(value = "FIELD", target = "Lnet/minecraft/Entity;lastTickPosZ:D"), @At(value = "FIELD", target = "Lnet/minecraft/Entity;posZ:D")})
    private double shadowSampleZ(double original, Entity entity) {
        return original + EMFManager.INSTANCE.getRenderVariable(entity, "render.shadow_offset_z");
    }

    @WrapOperation(method = "doRenderShadowAndFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/Render;renderEntityOnFire(Lnet/minecraft/Entity;DDDF)V"))
    private void moveFire(Render renderer, Entity entity, double x, double y, double z,
                          float delta, Operation<Void> original) {
        double width = EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_scale", entity.width);
        double height = EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_height", entity.height);
        if (width <= 0 || height <= 0) return;
        original.call(renderer, entity, x + EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_x"),
                y + EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_y"),
                z + EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_z"), delta);
    }

    @ModifyExpressionValue(method = "renderEntityOnFire", at = @At(value = "FIELD", target = "Lnet/minecraft/Entity;width:F"))
    private float fireWidth(float original, Entity entity) {
        return (float) EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_scale", original);
    }

    @ModifyExpressionValue(method = "renderEntityOnFire", at = @At(value = "FIELD", target = "Lnet/minecraft/Entity;height:F"))
    private float fireHeight(float original, Entity entity) {
        return (float) EMFManager.INSTANCE.getRenderVariable(entity, "render.fire_height", original);
    }
}
