package traben.entity_model_features.animation;

import net.minecraft.Entity;
import traben.entity_model_features.utils.EMFEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class AnimationControl {

    public static final Set<UUID> PAUSED = new HashSet<>();
    public static final Set<UUID> VANILLA = new HashSet<>();
    public static final Map<UUID, Object[]> PAUSED_PARTS = new HashMap<>();
    public static final List<Function<EMFEntity, Boolean>> PAUSE_LISTENERS = new ArrayList<>();
    public static final List<Function<EMFEntity, Boolean>> VANILLA_LISTENERS = new ArrayList<>();

    public static boolean shouldUseVanilla(Entity entity) {
        return test(entity, VANILLA, VANILLA_LISTENERS);
    }

    public static boolean isPaused(Entity entity) {
        return test(entity, PAUSED, PAUSE_LISTENERS);
    }

    private static boolean test(Entity entity, Set<UUID> explicit, List<Function<EMFEntity, Boolean>> listeners) {
        if (entity == null) return false;
        EMFEntity bridge = EMFEntity.of(entity);
        if (explicit.contains(bridge.etf$getUuid())) return true;
        for (Function<EMFEntity, Boolean> listener : listeners)
            if (Boolean.TRUE.equals(listener.apply(bridge))) return true;
        return false;
    }

    public static boolean partPaused(Entity entity, Object part) {
        if (entity == null) return false;
        Object[] paused = PAUSED_PARTS.get(EMFEntity.of(entity).etf$getUuid());
        if (paused != null) for (Object candidate : paused) if (candidate == part) return true;
        return false;
    }
}
