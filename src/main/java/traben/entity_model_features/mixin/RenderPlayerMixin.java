package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.Entity;
import net.minecraft.EntityPlayer;
import net.minecraft.AbstractClientPlayer;
import net.minecraft.Minecraft;
import net.minecraft.ModelBiped;
import net.minecraft.ModelRenderer;
import net.minecraft.RenderPlayer;
import net.minecraft.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.compat.ModCompat;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.ETFRenderContext;

@Mixin(RenderPlayer.class)
public abstract class RenderPlayerMixin {

    @Shadow
    private ModelBiped modelBipedMain;

    /** The player passed to renderSpecials, independent of a nested feature's texture context. */
    @Unique
    private AbstractClientPlayer emf$specialsPlayer;

    @Inject(method = "renderSpecials", at = @At("HEAD"))
    private void beginPlayerSpecials(AbstractClientPlayer player, float delta, CallbackInfo callbackInfo) {
        emf$specialsPlayer = player;
        ETFRenderContext.reassertEntity(player);
    }

    @Inject(method = "renderSpecials", at = @At("RETURN"))
    private void endPlayerSpecials(AbstractClientPlayer player, float delta, CallbackInfo callbackInfo) {
        emf$specialsPlayer = null;
    }

    @WrapOperation(method = "renderSpecials", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelRenderer;postRender(F)V"))
    private void positionAttachment(ModelRenderer part, float scale, Operation<Void> original) {
        String name = part == modelBipedMain.bipedHead ? "head_item" : "right_handheld_item";
        if (!RenderContext.applyAttachment(name, scale, true)) original.call(part, scale);
    }

    @WrapOperation(method = "renderSpecials", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelBiped;renderCloak(F)V"))
    private void renderCustomCape(ModelBiped model, float scale, Operation<Void> original) {
        if (ModCompat.shouldUseWaveyCapes()) {
            original.call(model, scale);
            return;
        }
        AbstractClientPlayer player = emf$specialsPlayer;
        if (player == null) {
            Entity context = ETFRenderContext.currentEntity();
            if (!(context instanceof AbstractClientPlayer)) {
                original.call(model, scale);
                return;
            }
            player = (AbstractClientPlayer) context;
        }
        ETFRenderContext.reassertEntity(player);
        if (!RenderContext.beginFeature(this, model, player, "player_cape", scale)) {
            original.call(model, scale);
            return;
        }
        // Fresh Animations' cape model is authored on top of a neutral cape
        // anchor.  EMF supplies the vanilla 2px offset here; the CEM body
        // compensation must not duplicate it.  The vanilla MITE matrix also
        // contains chasing-position translation and the vanilla hanging rotation;
        // retaining that matrix makes the custom root inherit a second,
        // incompatible anchor (most visible while flying).  Match EMF's
        // modern cape contract for every state: restore the call-site matrix,
        // apply MITE's armor-aware anchor, then draw the custom model.
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        if (player.inventory.armorItemInSlot(2) != null) GL11.glTranslatef(0, -0.0625F, 0.1875F);
        else GL11.glTranslatef(0, 0, 0.125F);
        GL11.glRotatef(180, 0, 1, 0);
        try { original.call(model, scale); }
        finally { RenderContext.end(); }
    }

    @WrapOperation(method = "renderSpecials", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelBiped;renderEars(F)V"))
    private void renderCustomEars(ModelBiped model, float scale, Operation<Void> original) {
        AbstractClientPlayer player = emf$specialsPlayer;
        if (player == null) {
            Entity context = ETFRenderContext.currentEntity();
            if (!(context instanceof AbstractClientPlayer)) {
                original.call(model, scale);
                return;
            }
            player = (AbstractClientPlayer) context;
        }
        ETFRenderContext.reassertEntity(player);
        if (!RenderContext.beginFeature(this, model, player, "player_ears", scale)) {
            original.call(model, scale);
            return;
        }
        try { original.call(model, scale); }
        finally { RenderContext.end(); }
    }

    @Inject(method = "renderFirstPersonArm", at = @At("HEAD"), cancellable = true)
    private void beginFirstPersonArm(EntityPlayer player, CallbackInfo callbackInfo) {
        ETFRenderContext.push(player);
        ETFRenderContext.setFirstPersonHand(true);
        ResourceLocation skin = Minecraft.getMinecraft().thePlayer.getLocationSkin();
        ResourceLocation selected = ETFApi.getVariantTexture(player, skin);
        Minecraft.getMinecraft().getTextureManager().bindTexture(selected);
        ETFRenderContext.setCurrentTexture(selected);
        RenderContext.beginEntity();
        RenderContext.beginNamed(this, modelBipedMain, player, false, "player",
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);
    }

    @Inject(method = "renderFirstPersonArm", at = @At("RETURN"))
    private void endFirstPersonArm(EntityPlayer player, CallbackInfo callbackInfo) {
        RenderContext.end();
        RenderContext.endEntity();
        ETFRenderContext.pop();
    }
}
