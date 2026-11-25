package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ModelRenderer;
import net.minecraft.RenderWitch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(RenderWitch.class)
public class RenderWitchMixin {

    @WrapOperation(method = "func_82411_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelRenderer;postRender(F)V"))
    private void positionAttachment(ModelRenderer part, float scale, Operation<Void> original) {
        if (!RenderContext.applyAttachment("witch_item", scale, true)) original.call(part, scale);
    }
}
