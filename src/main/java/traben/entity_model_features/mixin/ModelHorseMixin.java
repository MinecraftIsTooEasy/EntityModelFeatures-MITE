package traben.entity_model_features.mixin;

import net.minecraft.Entity;
import net.minecraft.ModelBase;
import net.minecraft.ModelHorse;
import net.minecraft.EntityHorse;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import traben.entity_model_features.EMFManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.model.RenderContext;

@Mixin(ModelHorse.class)
public abstract class ModelHorseMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/EntityHorse;isHorseSaddled()Z"))
    private boolean renderSeparateSaddle(EntityHorse horse, Operation<Boolean> original) {
        return original.call(horse) && !EMFManager.INSTANCE.hasHorseSaddleModel(horse, (ModelBase) (Object) this);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderModernHierarchy(Entity entity, float swing, float speed, float age,
                                      float yaw, float pitch, float scale, CallbackInfo callback) {
        if (RenderContext.renderBridgedModel((ModelBase) (Object) this, entity, swing, speed, age, yaw, pitch, scale))
            callback.cancel();
    }
}
