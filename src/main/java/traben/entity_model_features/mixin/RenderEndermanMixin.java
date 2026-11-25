package traben.entity_model_features.mixin;

import net.minecraft.EntityEnderman;
import net.minecraft.RenderEnderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.model.RenderContext;

@Mixin(RenderEnderman.class)
public class RenderEndermanMixin {

    @Inject(method = "renderCarrying", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 0))
    private void positionAttachment(EntityEnderman entity, float tickDelta, CallbackInfo ci) {
        RenderContext.applyAttachment("enderman_block", 0.0625F, false);
    }
}
