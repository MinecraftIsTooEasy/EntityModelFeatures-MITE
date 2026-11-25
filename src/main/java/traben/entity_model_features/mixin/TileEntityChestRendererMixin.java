package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.EnumChestType;
import net.minecraft.ModelChest;
import net.minecraft.ModelLargeChest;
import net.minecraft.TileEntityChest;
import net.minecraft.TileEntityChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;
import traben.entity_model_features.model.DoubleChestBridge;
import traben.entity_texture_features.features.ETFTileEntity;

@Mixin(TileEntityChestRenderer.class)
public class TileEntityChestRendererMixin {

    @WrapOperation(method = "renderTileEntityChestAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelChest;renderAll()V"))
    private void renderCustomChest(ModelChest model, Operation<Void> original, TileEntityChest tile,
                                   double x, double y, double z, float tickDelta) {
        String name = tile.getChestType() == EnumChestType.trapped ? "trapped_chest" : "chest";
        if (model instanceof ModelLargeChest) {
            if (DoubleChestBridge.render(this, model, tile, name, tickDelta)) return;
            name += "_large";
        } else if (DoubleChestBridge.renderSingle(this, model, ETFTileEntity.of(tile), name, tickDelta)) {
            return;
        }
        RenderContext.beginIsolatedNamed(this, model, ETFTileEntity.of(tile), name,
                0, 0, tile.getWorldObj() == null ? 0 : tile.getWorldObj().getTotalWorldTime() + tickDelta, 0, 0, 0.0625F);
        try { original.call(model); } finally { RenderContext.end(); }
    }
}
