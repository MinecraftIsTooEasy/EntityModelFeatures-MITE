package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.ModelChicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(ModelChicken.class)
public abstract class ModelChickenMixin {

    @ModifyExpressionValue(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/ModelChicken;isChild:Z"))
    private boolean useBakedChildPose(boolean child) {
        return RenderContext.useLegacyChildTransform(child);
    }
}
