package traben.entity_model_features.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.EntityHorse;
import net.minecraft.Entity;
import net.minecraft.Minecraft;
import net.minecraft.ModelBase;
import net.minecraft.RenderHorse;
import net.minecraft.RenderLiving;
import net.minecraft.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.model.RenderContext;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.features.ETFRenderContext;

import java.util.Arrays;

@Mixin(RenderHorse.class)
public abstract class RenderHorseMixin extends RenderLiving {

    protected RenderHorseMixin(ModelBase model, float shadow) { super(model, shadow); }

    @WrapOperation(method = "func_110846_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/ModelBase;render(Lnet/minecraft/Entity;FFFFFF)V"))
    private void renderCustomHorse(ModelBase model, Entity horse, float limbSwing, float limbSpeed,
                                   float age, float headYaw, float headPitch, float scale,
                                   Operation<Void> original) {
        RenderContext.begin(this, model, horse, false, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        try {
            original.call(model, horse, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        } finally {
            RenderContext.end();
        }
    }

    @WrapOperation(method = "func_110848_b", at = @At(value = "INVOKE", target = "Lnet/minecraft/EntityHorse;getHorseTexture()Ljava/lang/String;"))
    private String separateArmorTextureCache(EntityHorse horse, Operation<String> original) {
        String key = original.call(horse);
        return EMFManager.INSTANCE.hasModel("horse_armor", horse) ? key + "_emf_separate_armor" : key;
    }

    @WrapOperation(method = "func_110848_b", at = @At(value = "INVOKE", target = "Lnet/minecraft/EntityHorse;getVariantTexturePaths()[Ljava/lang/String;"))
    private String[] removeArmorFromLayeredTexture(EntityHorse horse, Operation<String[]> original) {
        String[] paths = original.call(horse);
        if (paths.length < 3 || paths[2] == null || !EMFManager.INSTANCE.hasModel("horse_armor", horse)) {
            return paths;
        }
        String[] withoutArmor = Arrays.copyOf(paths, paths.length);
        withoutArmor[2] = null;
        return withoutArmor;
    }

    @Inject(method = "func_110846_a", at = @At("RETURN"))
    private void renderCustomHorseSaddle(EntityHorse horse, float limbSwing, float limbSpeed,
                                        float age, float headYaw, float headPitch, float scale,
                                        CallbackInfo callbackInfo) {
        if (horse.isInvisible() || !horse.isAdultHorse() || !horse.isHorseSaddled()
                || !EMFManager.INSTANCE.hasHorseSaddleModel(horse, mainModel)) return;
        String name = EMFManager.horseSaddleModelName(horse);
        ResourceLocation previous = ETFRenderContext.currentTexture();
        ResourceLocation texture = new ResourceLocation("minecraft", "textures/entity/equipment/" + name + "/saddle.png");
        // Older CEM packs can still put tack on the entity skin.
        if (!ETFApi.resourceExists(texture)) texture = previous;
        if (texture == null) return;
        texture = ETFApi.getVariantTexture(horse, texture);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        ETFRenderContext.setCurrentTexture(texture);
        RenderContext.beginNamed(this, mainModel, horse, true, name,
                limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        try {
            mainModel.render(horse, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        } finally {
            RenderContext.end();
            if (previous != null) Minecraft.getMinecraft().getTextureManager().bindTexture(previous);
            ETFRenderContext.setCurrentTexture(previous);
        }
    }

    @Inject(method = "func_110846_a", at = @At("RETURN"))
    private void renderCustomHorseArmor(EntityHorse horse, float limbSwing, float limbSpeed,
                                        float age, float headYaw, float headPitch, float scale,
                                        CallbackInfo callbackInfo) {
        String[] paths = horse.getVariantTexturePaths();
        if (horse.isInvisible() || paths.length < 3 || paths[2] == null ||
                !EMFManager.INSTANCE.hasModel("horse_armor", horse)) {
            return;
        }
        ResourceLocation previous = ETFRenderContext.currentTexture();
        ResourceLocation armor = ETFApi.getVariantTexture(horse, new ResourceLocation(paths[2], false));
        Minecraft.getMinecraft().getTextureManager().bindTexture(armor);
        ETFRenderContext.setCurrentTexture(armor);
        RenderContext.beginNamed(this, mainModel, horse, true, "horse_armor",
                limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        try {
            mainModel.render(horse, limbSwing, limbSpeed, age, headYaw, headPitch, scale);
        } finally {
            RenderContext.end();
            if (previous != null) {
                Minecraft.getMinecraft().getTextureManager().bindTexture(previous);
            }
            ETFRenderContext.setCurrentTexture(previous);
        }
    }
}
