package traben.entity_model_features.animation;

import net.minecraft.Entity;
import net.minecraft.EntityBlaze;
import net.minecraft.EntityHorse;
import net.minecraft.EntityVillager;
import net.minecraft.Minecraft;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.model.CustomModel;
import traben.entity_texture_features.features.ETFRenderContext;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Upstream frame cadence, kept independent of draw count and emissive/layer passes. */
public final class AnimationLOD {

    private record Timer(long frame, int remaining, boolean skip) { }
    private static final Map<Entity, Map<CustomModel, Timer>> TIMERS = new WeakHashMap<>();
    public static double lastFOV = 70;

    public static boolean skip(Entity entity, CustomModel model) {
        EMFConfig config = EMFConfig.get();
        if (entity == null || config.animationLODDistance <= 0 || ETFRenderContext.isInGui()
                || ETFRenderContext.isFirstPersonHand() || entity instanceof EntityVillager
                || entity instanceof EntityHorse || entity instanceof EntityBlaze) return false;
        Minecraft client = Minecraft.getMinecraft();
        if (client == null || client.thePlayer == null) return false;
        long frame = EMFManager.INSTANCE.frameSequence();
        Map<CustomModel, Timer> timers = TIMERS.computeIfAbsent(entity, ignored -> new IdentityHashMap<>());
        Timer previous = timers.get(model);
        if (previous != null && previous.frame == frame) return previous.skip;
        int remaining = previous == null || previous.remaining < 1 ? factor(entity, client, config) : previous.remaining - 1;
        boolean skip = remaining > 0;
        timers.put(model, new Timer(frame, remaining, skip));
        return skip;
    }

    private static int factor(Entity entity, Minecraft client, EMFConfig config) {
        int distance = (int) entity.getDistanceToEntity(client.thePlayer);
        int factor = (int) ((distance / config.animationLODDistance) * lastFOV / 70);
        if (config.retainDetailOnLowFps) {
            double fps = 1 / EMFManager.INSTANCE.getFrameTime();
            if (fps < 59) factor = (int) (factor * fps / 60);
        }
        float size = Math.max(entity.width, entity.height);
        if (config.retainDetailOnLargerMobs && size > 2) factor = (int) (factor / (size / 2));
        return factor;
    }

    public static void clear() { TIMERS.clear(); }
}
