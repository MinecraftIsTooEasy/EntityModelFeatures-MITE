package traben.entity_model_features.mixin;

import net.minecraft.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.animation.AnimationLOD;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void beginAnimationFrame(float partialTick, CallbackInfo callbackInfo) {
        EMFManager.INSTANCE.beginFrame(partialTick);
    }

    @Inject(method = "getFOVModifier", at = @At("RETURN"))
    private void captureAnimationFov(float delta, boolean world, CallbackInfoReturnable<Float> callback) {
        if (world) AnimationLOD.lastFOV = callback.getReturnValue();
    }
}
