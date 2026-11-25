package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.ModelSkeletonHead;
import net.minecraft.TileEntitySkullRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.features.ETFRenderContext;

@Mixin(TileEntitySkullRenderer.class)
public class TileEntitySkullRendererMixin {

    @WrapOperation(method = "func_82393_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelSkeletonHead;render(Lnet/minecraft/Entity;FFFFFF)V"))
    private void renderCustomSkull(ModelSkeletonHead model, Entity entity, float limbSwing, float limbSpeed,
                                   float age, float yaw, float pitch, float scale, Operation<Void> original,
                                   float x, float y, float z, int facing, float rotation, int type, String owner) {
        String name = switch (type) {
            case 1 -> "head_wither_skeleton";
            case 2 -> "head_zombie";
            case 3 -> "head_player";
            case 4 -> "head_creeper";
            default -> "head_skeleton";
        };
        RenderContext.beginIsolatedNamed(this, model, ETFRenderContext.currentEntity(), name,
                limbSwing, limbSpeed, age, yaw, pitch, scale);
        try { original.call(model, entity, limbSwing, limbSpeed, age, yaw, pitch, scale); }
        finally { RenderContext.end(); }
    }
}
