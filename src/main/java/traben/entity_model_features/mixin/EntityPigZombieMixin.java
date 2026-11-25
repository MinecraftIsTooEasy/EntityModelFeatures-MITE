package traben.entity_model_features.mixin;

import net.minecraft.EntityPigZombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPigZombie.class)
public interface EntityPigZombieMixin {

    @Accessor("angerLevel")
    int emf$getAngerLevel();
}
