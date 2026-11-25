package traben.entity_model_features.compat;

import net.minecraft.Entity;
import net.minecraft.ModelBiped;
import moddedmite.rustedironcore.api.entity.EntityIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Small MITE equivalent of EMF Compat Core's pose manager.
 *
 * The legacy renderer reuses one ModelBiped for all player render passes. A
 * per-entity snapshot is therefore safer than storing state on a model
 * instance: the main player model is captured first, then armour/overlay
 * passes consume the same NEA pose instead of replacing it.
 */
public final class PoseManager {

    private static final Map<UUID, SavedPoses> SAVED = new HashMap<>();
    private static final Map<UUID, ActiveParts> ACTIVE = new HashMap<>();

    private PoseManager() {
    }

    public static void beginEntity(Entity entity) {
        UUID id = id(entity);
        if (id == null) return;
        SAVED.remove(id);
        ACTIVE.remove(id);
    }

    public static void endEntity(Entity entity) {
        UUID id = id(entity);
        if (id == null) return;
        SAVED.remove(id);
        ACTIVE.remove(id);
    }

    /**
     * Capture only once per renderer entity scope. This intentionally keeps
     * the main player pose when the renderer subsequently draws armour passes.
     */
    public static void capture(Entity entity, ModelBiped model, ActiveParts active) {
        UUID id = id(entity);
        if (id == null || model == null || active == null) return;
        if (!active.any()) {
            SAVED.remove(id);
            ACTIVE.remove(id);
            return;
        }
        if (SAVED.containsKey(id)) return;
        Map<String, PoseSnapshot> parts = new HashMap<>();
        if (active.leftLeg()) parts.put("left_leg", new PoseSnapshot(model.bipedLeftLeg));
        if (active.rightLeg()) parts.put("right_leg", new PoseSnapshot(model.bipedRightLeg));
        SAVED.put(id, new SavedPoses(
                active.leftArm() ? new PoseSnapshot(model.bipedLeftArm) : null,
                active.rightArm() ? new PoseSnapshot(model.bipedRightArm) : null,
                parts));
        ACTIVE.put(id, active);
    }

    public static SavedPoses getSavedPoses(Entity entity) {
        UUID id = id(entity);
        return id == null ? null : SAVED.get(id);
    }

    public static ActiveParts getActiveParts(Entity entity) {
        UUID id = id(entity);
        return id == null ? null : ACTIVE.get(id);
    }

    public static boolean isActive(Entity entity, String part) {
        ActiveParts active = getActiveParts(entity);
        if (active == null) return false;
        return switch (part) {
            case "left_arm", "left_sleeve" -> active.leftArm();
            case "right_arm", "right_sleeve" -> active.rightArm();
            case "left_leg", "left_pants" -> active.leftLeg();
            case "right_leg", "right_pants" -> active.rightLeg();
            default -> false;
        };
    }

    /** Restore action rotations while CEM retains the animated shoulder pivots. */
    public static void restore(Entity entity, ModelBiped model) {
        SavedPoses poses = getSavedPoses(entity);
        if (poses == null || model == null) return;
        if (poses.leftArm() != null) poses.leftArm().applyRotation(model.bipedLeftArm);
        if (poses.rightArm() != null) poses.rightArm().applyRotation(model.bipedRightArm);
        PoseSnapshot leftLeg = poses.part("left_leg");
        PoseSnapshot rightLeg = poses.part("right_leg");
        if (leftLeg != null) leftLeg.apply(model.bipedLeftLeg);
        if (rightLeg != null) rightLeg.apply(model.bipedRightLeg);
    }

    /** Restore only the limb rotations for a held-item attachment. */
    public static void restoreArmRotation(Entity entity, ModelBiped model, boolean left) {
        SavedPoses poses = getSavedPoses(entity);
        if (poses == null || model == null) return;
        PoseSnapshot pose = left ? poses.leftArm() : poses.rightArm();
        if (pose != null) pose.applyRotation(left ? model.bipedLeftArm : model.bipedRightArm);
    }

    public static void clear(Entity entity) {
        UUID id = id(entity);
        if (id == null) return;
        SAVED.remove(id);
        ACTIVE.remove(id);
    }

    private static UUID id(Entity entity) {
        return EntityIdentity.uuid(entity);
    }
}
