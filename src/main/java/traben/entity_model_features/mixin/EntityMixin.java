package traben.entity_model_features.mixin;

import net.minecraft.Entity;
import org.spongepowered.asm.mixin.Mixin;
import traben.entity_model_features.utils.EMFEntity;

@Mixin(Entity.class)
public abstract class EntityMixin implements EMFEntity {
}
