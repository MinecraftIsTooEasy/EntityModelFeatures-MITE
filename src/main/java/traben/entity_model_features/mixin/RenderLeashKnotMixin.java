package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.ModelLeashKnot;
import net.minecraft.RenderLeashKnot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(RenderLeashKnot.class)
public class RenderLeashKnotMixin {

    @WrapOperation(method = "func_110799_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelLeashKnot;render(Lnet/minecraft/Entity;FFFFFF)V"))
    private void renderCustomModel(ModelLeashKnot model, Entity entity, float limbSwing, float limbSpeed,
                                   float age, float headYaw, float headPitch, float scale,
                                   Operation<Void> original) {
        RenderContext.begin(this, model, entity, false, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        try {
            original.call(model, entity, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        } finally {
            RenderContext.end();
        }
    }
}

