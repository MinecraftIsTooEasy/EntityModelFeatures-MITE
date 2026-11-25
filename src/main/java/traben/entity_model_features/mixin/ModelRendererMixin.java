package traben.entity_model_features.mixin;

import net.minecraft.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.model.RenderContext;
import traben.entity_model_features.model.PartGeometry;
import traben.entity_texture_features.features.EmissiveRenderer;
import org.lwjgl.opengl.GL11;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import traben.entity_model_features.model.DebugGeometry;

@Mixin(ModelRenderer.class)
public abstract class ModelRendererMixin implements PartGeometry {

    @Shadow
    private boolean compiled;
    @Shadow
    private int displayList;
    @Shadow
    private void compileDisplayList(float scale) { throw new AssertionError(); }

    @Override
    public void emf$drawBoxes(float scale) {
        if (!compiled) compileDisplayList(scale);
        DebugGeometry.draw(() -> EmissiveRenderer.draw(() -> GL11.glCallList(displayList)), false);
    }

    @WrapOperation(method = {"render", "renderWithRotation"}, at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V"))
    private void drawDebugGeometry(int list, Operation<Void> original) {
        DebugGeometry.draw(() -> original.call(list), false);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderCustomPart(float scale, CallbackInfo callbackInfo) {
        ModelRenderer part = (ModelRenderer) (Object) this;
        if (RenderContext.render(part, scale) || RenderContext.renderExtended(part, scale, false, this)) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "renderWithRotation", at = @At("HEAD"), cancellable = true)
    private void renderRotatedCustomPart(float scale, CallbackInfo callbackInfo) {
        ModelRenderer part = (ModelRenderer) (Object) this;
        if (RenderContext.render(part, scale, true) || RenderContext.renderExtended(part, scale, true, this)) callbackInfo.cancel();
    }

    @Inject(method = "postRender", at = @At("HEAD"), cancellable = true)
    private void applyCustomAttachmentTransform(float scale, CallbackInfo callbackInfo) {
        if (RenderContext.postRender((ModelRenderer) (Object) this, scale)) {
            callbackInfo.cancel();
        }
    }
}
