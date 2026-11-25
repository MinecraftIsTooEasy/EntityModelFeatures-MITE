package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.EntityLiving;
import net.minecraft.RenderLiving;
import net.minecraft.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.animation.RenderMath;

@Mixin(RenderLiving.class)
public abstract class RenderLivingMixin {

    @Unique
    private int emf$leashVertex;

    @WrapOperation(method = "doRenderLiving(Lnet/minecraft/EntityLiving;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/RenderLiving;func_110827_b(Lnet/minecraft/EntityLiving;DDDFF)V"))
    private void beginLeashDraw(RenderLiving renderer, EntityLiving entity, double x, double y, double z,
                                float yaw, float delta, Operation<Void> original) {
        int previous = emf$leashVertex;
        emf$leashVertex = 0;
        try { original.call(renderer, entity, x, y, z, yaw, delta); }
        finally { emf$leashVertex = previous; }
    }

    @WrapOperation(method = "func_110827_b", at = @At(value = "INVOKE", target = "Lnet/minecraft/Tessellator;addVertex(DDD)V"))
    private void moveLeashEndpoints(Tessellator tessellator, double x, double y, double z,
                                    Operation<Void> original, EntityLiving entity,
                                    double renderX, double renderY, double renderZ, float yaw, float delta) {
        EMFManager manager = EMFManager.INSTANCE;
        // Upstream adds world-space offsets. Indexed offsets belong to modern quad leashes.
        double ox = manager.getRenderVariable(entity, "render.leash_offset_x");
        double oy = manager.getRenderVariable(entity, "render.leash_offset_y");
        double oz = manager.getRenderVariable(entity, "render.leash_offset_z");
        Entity holder = entity.getLeashedToEntity();
        double hx = holder == null ? 0 : manager.getRenderVariable(holder, "render.leash_holder_offset_x");
        double hy = holder == null ? 0 : manager.getRenderVariable(holder, "render.leash_holder_offset_y");
        double hz = holder == null ? 0 : manager.getRenderVariable(holder, "render.leash_holder_offset_z");
        int index = emf$leashVertex++;
        original.call(tessellator, x + RenderMath.leashOffset(index, ox, hx, false),
                y + RenderMath.leashOffset(index, oy, hy, true),
                z + RenderMath.leashOffset(index, oz, hz, false));
    }
}
