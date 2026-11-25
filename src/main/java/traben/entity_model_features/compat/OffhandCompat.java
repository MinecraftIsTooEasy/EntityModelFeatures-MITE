package traben.entity_model_features.compat;

import moddedmite.offhand.api.core.IOffhandPlayer;
import moddedmite.offhand.api.core.OffhandUtils;
import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;

final class OffhandCompat {

    static boolean isRenderContext() {
        return OffhandUtils.isClientOffhandRenderContext();
    }

    static ItemStack getItem(EntityPlayer player) {
        return OffhandUtils.getOffhandItem(player);
    }

    static float getSwingProgress(EntityPlayer player, float partialTick) {
        return ((IOffhandPlayer) player).getOffSwingProgress(partialTick);
    }

    static boolean isItemInUse(EntityPlayer player) {
        return ((IOffhandPlayer) player).isOffhandItemInUse();
    }

    static boolean isBlocking(EntityPlayer player) {
        return ((IOffhandPlayer) player).isOffhandBlocking();
    }
}
