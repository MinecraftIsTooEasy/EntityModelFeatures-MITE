package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ModelBase;
import net.minecraft.ModelEnderCrystal;
import net.minecraft.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.model.SplitCrystalModel;

@Mixin(ModelEnderCrystal.class)
public abstract class ModelEnderCrystalMixin extends ModelBase implements SplitCrystalModel {

    @Shadow private ModelRenderer glass;
    @Unique private ModelRenderer emf$innerGlass;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void separateInnerGlass(float scale, boolean base, CallbackInfo ci) {
        emf$innerGlass = new ModelRenderer(this, "inner_glass");
        emf$innerGlass.setTextureSize((int) glass.textureWidth, (int) glass.textureHeight);
        emf$innerGlass.cubeList.addAll(glass.cubeList);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelRenderer;render(F)V", ordinal = 2))
    private void renderInnerGlass(ModelRenderer part, float scale, Operation<Void> original) {
        original.call(emf$innerGlass, scale);
    }

    @Override
    public ModelRenderer emf$getInnerGlass() { return emf$innerGlass; }
}
