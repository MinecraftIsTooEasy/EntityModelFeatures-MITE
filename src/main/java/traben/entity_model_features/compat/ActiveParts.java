package traben.entity_model_features.compat;

/** The body parts currently owned by a third-person action animation. */
public record ActiveParts(boolean leftArm, boolean rightArm, boolean leftLeg, boolean rightLeg) {

    public ActiveParts(boolean leftArm, boolean rightArm) {
        this(leftArm, rightArm, false, false);
    }

    public boolean any() {
        return leftArm || rightArm || leftLeg || rightLeg;
    }
}
