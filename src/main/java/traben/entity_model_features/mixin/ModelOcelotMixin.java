package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.ModelOcelot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(ModelOcelot.class)
public abstract class ModelOcelotMixin {

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/ModelOcelot;isChild:Z"))
    private boolean useBakedChildPose(boolean child) {
        return RenderContext.useLegacyChildTransform(child);
    }
}
