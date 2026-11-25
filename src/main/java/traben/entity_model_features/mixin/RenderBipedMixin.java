package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ModelBiped;
import net.minecraft.ModelRenderer;
import net.minecraft.RenderBiped;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;

@Mixin(RenderBiped.class)
public abstract class RenderBipedMixin {

    @Shadow protected ModelBiped modelBipedMain;

    @WrapOperation(method = "func_130005_c", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelRenderer;postRender(F)V"))
    private void positionAttachment(ModelRenderer part, float scale, Operation<Void> original) {
        String name = part == modelBipedMain.bipedHead ? "head_item" : "right_handheld_item";
        if (!RenderContext.applyAttachment(name, scale, true)) original.call(part, scale);
    }
}
