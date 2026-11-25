package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.ModelChest;
import net.minecraft.TileEntityEnderChest;
import net.minecraft.TileEntityEnderChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;
import traben.entity_model_features.model.DoubleChestBridge;
import traben.entity_texture_features.features.ETFTileEntity;

@Mixin(TileEntityEnderChestRenderer.class)
public class TileEntityEnderChestRendererMixin {

    @WrapOperation(method = "renderEnderChest", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelChest;renderAll()V"))
    private void renderCustomChest(ModelChest model, Operation<Void> original, TileEntityEnderChest tile,
                                   double x, double y, double z, float tickDelta) {
        if (DoubleChestBridge.renderSingle(this, model, ETFTileEntity.of(tile), "ender_chest", tickDelta)) return;
        RenderContext.beginIsolatedNamed(this, model, ETFTileEntity.of(tile), "ender_chest", 0, 0,
                tile.getWorldObj() == null ? 0 : tile.getWorldObj().getTotalWorldTime() + tickDelta, 0, 0, 0.0625F);
        try { original.call(model); } finally { RenderContext.end(); }
    }
}
