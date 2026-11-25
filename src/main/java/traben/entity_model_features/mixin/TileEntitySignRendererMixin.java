package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ModelSign;
import net.minecraft.TileEntitySign;
import net.minecraft.TileEntitySignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.features.ETFTileEntity;

@Mixin(TileEntitySignRenderer.class)
public class TileEntitySignRendererMixin {

    @WrapOperation(method = "renderTileEntitySignAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelSign;renderSign()V"))
    private void renderCustomSign(ModelSign model, Operation<Void> original, TileEntitySign tile,
                                  double x, double y, double z, float tickDelta) {
        ETFTileEntity subject = ETFTileEntity.of(tile);
        String variant = model.signStick.showModel ? "oak_sign" : "oak_wall_sign";
        String name = EMFManager.INSTANCE.hasModel(variant, subject) ? variant
                : !model.signStick.showModel && EMFManager.INSTANCE.hasModel("wall_sign", subject) ? "wall_sign" : "sign";
        RenderContext.beginIsolatedNamed(this, model, subject, name, 0, 0,
                tile.getWorldObj() == null ? 0 : tile.getWorldObj().getTotalWorldTime() + tickDelta, 0, 0, 0.0625F);
        try { original.call(model); } finally { RenderContext.end(); }
    }
}
