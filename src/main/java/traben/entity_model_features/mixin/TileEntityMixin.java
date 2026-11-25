package traben.entity_model_features.mixin;

import net.minecraft.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import traben.entity_model_features.utils.EMFEntity;

@Mixin(TileEntity.class)
public abstract class TileEntityMixin implements EMFEntity {
}
