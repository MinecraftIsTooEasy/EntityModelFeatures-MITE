package traben.entity_model_features.mixin;

import net.minecraft.Minecraft;
import net.minecraft.RaycastCollision;
import net.minecraft.ReloadableResourceManager;
import net.minecraft.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.config.EMFConfig;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public abstract ResourceManager getResourceManager();

    @Shadow
    public RaycastCollision objectMouseOver;

    @Inject(method = "startGame", at = @At("RETURN"))
    private void registerReloadListener(CallbackInfo callbackInfo) {
        ((ReloadableResourceManager) getResourceManager()).registerReloadListener(EMFManager.INSTANCE);
    }

    @Inject(method = "clickMouse(I)V", at = @At("HEAD"))
    private void requestModelDebug(int button, CallbackInfo callbackInfo) {
        if (button == 1 && EMFConfig.get().debugOnRightClick && objectMouseOver != null
                && objectMouseOver.isEntity()) {
            EMFManager.INSTANCE.requestDebug(objectMouseOver.getEntityHit());
        }
    }
}
