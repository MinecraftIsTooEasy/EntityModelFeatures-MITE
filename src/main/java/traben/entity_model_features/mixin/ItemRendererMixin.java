package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import moddedmite.rustedironcore.api.model.JsonHeldItemTransforms;
import moddedmite.rustedironcore.api.model.JsonModelDisplayContext;
import moddedmite.rustedironcore.api.model.JsonModernHeldItemRenderer;
import net.minecraft.EntityLivingBase;
import net.minecraft.ItemRenderer;
import net.minecraft.ItemStack;
import net.minecraft.ItemSword;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_texture_features.features.ETFRenderContext;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @WrapOperation(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/ItemRenderer;renderItem(Lnet/minecraft/EntityLivingBase;Lnet/minecraft/ItemStack;I)V"))
    private void renderFirstPersonSword(ItemRenderer renderer, EntityLivingBase entity, ItemStack stack, int pass,
                                        Operation<Void> original) {
        if (stack == null || !(stack.getItem() instanceof ItemSword)) {
            original.call(renderer, entity, stack, pass);
            return;
        }

        ETFRenderContext.withScope(ETFRenderContext.Scope.HAND, () -> {
            JsonHeldItemTransforms.apply(entity, stack, JsonModelDisplayContext.FIRST_PERSON_RIGHT_HAND);
            JsonModernHeldItemRenderer.render(renderer, entity, stack, JsonModelDisplayContext.FIRST_PERSON_RIGHT_HAND);
        });
    }
}
