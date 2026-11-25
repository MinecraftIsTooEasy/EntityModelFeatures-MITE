package traben.entity_model_features.mixin;

import net.minecraft.EntityMooshroom;
import net.minecraft.RenderMooshroom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.EMFManager;

@Mixin(RenderMooshroom.class)
public abstract class RenderMooshroomMixin {

    @Inject(method = "renderMooshroomEquippedItems(Lnet/minecraft/EntityMooshroom;F)V", at = @At("HEAD"), cancellable = true)
    private void suppressVanillaMushrooms(EntityMooshroom entity, float tickDelta, CallbackInfo callbackInfo) {
        if (EMFManager.INSTANCE.isModelSelected(entity, "mooshroom")
                || EMFManager.INSTANCE.isModelSelected(entity, "mooshroom_baby")) {
            callbackInfo.cancel();
        }
    }
}
