package traben.entity_model_features.model;

import net.minecraft.ModelBase;
import net.minecraft.ModelBiped;
import net.minecraft.ModelChicken;
import net.minecraft.ModelCow;
import net.minecraft.ModelOcelot;
import net.minecraft.ModelPig;
import net.minecraft.ModelSheep1;
import net.minecraft.ModelSheep2;
import net.minecraft.ModelWolf;
import net.minecraft.ModelVillager;
import traben.entity_model_features.animation.RenderParameters;

import java.util.Set;

/** 1.21.11 BabyModelTransform: translate and scale each root's initial PartPose before CEM animation. */
public record BabyModelPose(float headScale, float headY, float headZ,
                            float bodyScale, float bodyY, Set<String> headParts) {

    public static BabyModelPose forModel(ModelBase model) {
        if (model instanceof ModelBiped) return new BabyModelPose(.75F, 16, 0, .5F, 24, Set.of("head", "headwear"));
        if (model instanceof ModelCow) return new BabyModelPose(1, 8, 6, .5F, 24, Set.of("head"));
        if (model instanceof ModelPig) return new BabyModelPose(1, 4, 4, .5F, 24, Set.of("head"));
        if (model instanceof ModelSheep1 || model instanceof ModelSheep2)
            return new BabyModelPose(1, 8, 4, .5F, 24, Set.of("head"));
        if (model instanceof ModelChicken)
            return new BabyModelPose(1, 5, 2, 1 / 1.99F, 24, Set.of("head", "bill", "chin"));
        if (model instanceof ModelOcelot) return new BabyModelPose(.75F, 10, 4, .5F, 24, Set.of("head"));
        if (model instanceof ModelWolf) return new BabyModelPose(1, 5, 2, .5F, 24, Set.of("head"));
        // VillagerModel uses MeshTransformer.scaling(.5), whose root lift is 24.016 * (1 - scale).
        if (model instanceof ModelVillager) return new BabyModelPose(.5F, 24.016F, 0, .5F, 24.016F, Set.of());
        if (model instanceof ModernHorseModel)
            return new BabyModelPose(1.5F / 2.7272F, 16.2F, 1.36F, .5F, 20, Set.of("neck"));
        return null;
    }

    void apply(ModelStructure structure, RenderParameters parameters) {
        structure.parts().forEach((part, name) -> {
            // Virtual and native children inherit their root transform once.
            if (structure.isChildPart(part)) return;
            boolean head = headParts.contains(name);
            float scale = head ? headScale : bodyScale;
            parameters.applyInheritedBipedPose(part, part.rotationPointX * scale,
                    (part.rotationPointY + (head ? headY : bodyY)) * scale,
                    (part.rotationPointZ + (head ? headZ : 0)) * scale,
                    part.rotateAngleX, part.rotateAngleY, part.rotateAngleZ, scale, scale, scale);
        });
    }
}
