package traben.entity_model_features.compat;

import com.inf1nlty.elytra.ElytraPhysics;
import net.minecraft.EntityPlayer;

final class ElytraCompat {

    static boolean isFlying(EntityPlayer player) {
        return ElytraPhysics.getElytraFlying(player);
    }
}
