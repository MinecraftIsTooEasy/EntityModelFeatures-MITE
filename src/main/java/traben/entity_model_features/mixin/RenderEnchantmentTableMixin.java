package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.ModelBook;
import net.minecraft.RenderEnchantmentTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.features.ETFRenderContext;

@Mixin(RenderEnchantmentTable.class)
public class RenderEnchantmentTableMixin {

    @WrapOperation(method = "renderTileEntityEnchantmentTableAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelBook;render(Lnet/minecraft/Entity;FFFFFF)V"))
    private void renderCustomBook(ModelBook model, Entity entity, float limbSwing, float limbSpeed,
                                  float age, float yaw, float pitch, float scale, Operation<Void> original) {
        Entity subject = ETFRenderContext.currentEntity();
        String name = EMFManager.INSTANCE.hasModel("enchanting_book", subject) ? "enchanting_book" : "book";
        RenderContext.beginIsolatedNamed(this, model, subject, name, limbSwing, limbSpeed, age, yaw, pitch, scale);
        try { original.call(model, entity, limbSwing, limbSpeed, age, yaw, pitch, scale); }
        finally { RenderContext.end(); }
    }
}
