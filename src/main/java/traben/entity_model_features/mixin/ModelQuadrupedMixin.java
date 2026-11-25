package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.ModelQuadruped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(ModelQuadruped.class)
public abstract class ModelQuadrupedMixin {

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/ModelQuadruped;isChild:Z"))
    private boolean useBakedChildPose(boolean child) {
        return RenderContext.useLegacyChildTransform(child);
    }
}
