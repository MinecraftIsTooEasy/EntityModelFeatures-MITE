package traben.entity_model_features.compat;

import net.minecraft.EntityPlayer;
import net.minecraft.ItemStack;
import net.xiaoyu233.fml.FishModLoader;

public final class ModCompat {

    private static final String OFFHAND_MOD_ID = "offhand";
    private static final String ELYTRA_MOD_ID = "elytra";
    private static final String WAVEYCAPES_MOD_ID = "waveycapes";

    public static boolean hasOffhand() {
        return FishModLoader.hasMod(OFFHAND_MOD_ID);
    }

    public static boolean hasElytra() {
        return FishModLoader.hasMod(ELYTRA_MOD_ID);
    }

    public static boolean hasWaveyCapes() {
        return FishModLoader.hasMod(WAVEYCAPES_MOD_ID);
    }

    public static boolean shouldUseWaveyCapes() {
        return hasWaveyCapes() && WaveyCapesCompat.shouldReplaceVanillaCape();
    }

    public static boolean isOffhandRenderContext() {
        return hasOffhand() && OffhandCompat.isRenderContext();
    }

    public static ItemStack getOffhandItem(EntityPlayer player) {
        if (!hasOffhand() || player == null) return null;
        return OffhandCompat.getItem(player);
    }

    public static boolean hasOffhandItem(EntityPlayer player) {
        ItemStack stack = getOffhandItem(player);
        return stack != null && stack.stackSize > 0 && stack.getItem() != null;
    }

    public static float getOffhandSwingProgress(EntityPlayer player, float partialTick) {
        if (!hasOffhand() || player == null) return 0.0F;
        return OffhandCompat.getSwingProgress(player, partialTick);
    }

    public static boolean isOffhandItemInUse(EntityPlayer player) {
        if (!hasOffhand() || player == null) return false;
        return OffhandCompat.isItemInUse(player);
    }

    public static boolean isOffhandBlocking(EntityPlayer player) {
        if (!hasOffhand() || player == null) return false;
        return OffhandCompat.isBlocking(player);
    }

    public static boolean isElytraFlying(EntityPlayer player) {
        if (!hasElytra() || player == null) return false;
        return ElytraCompat.isFlying(player);
    }

    public static boolean hasCurrentItem(EntityPlayer player) {
        if (player == null || player.inventory == null) return false;
        ItemStack stack = player.inventory.getCurrentItemStack();
        return stack != null && stack.stackSize > 0 && stack.getItem() != null;
    }
}
