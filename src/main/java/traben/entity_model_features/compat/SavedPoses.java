package traben.entity_model_features.compat;

import java.util.Map;

/** Snapshot set shared by EMF and the MITE NEA port. */
public record SavedPoses(PoseSnapshot leftArm, PoseSnapshot rightArm,
                         Map<String, PoseSnapshot> parts) {

    public PoseSnapshot part(String name) {
        return parts == null ? null : parts.get(name);
    }
}
