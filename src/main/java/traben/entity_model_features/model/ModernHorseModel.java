package traben.entity_model_features.model;

import net.minecraft.Entity;
import net.minecraft.EntityHorse;
import net.minecraft.ModelBase;
import net.minecraft.ModelRenderer;
import org.lwjgl.opengl.GL11;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_model_features.EMFManager;

import java.util.List;

/** The pre-1.21.2 CEM horse hierarchy; native MITE's shins and hooves remain in its fallback model. */
public final class ModernHorseModel extends ModelBase {

    public final ModelRenderer body;
    public final ModelRenderer neck;
    public final ModelRenderer head;
    public final ModelRenderer mouth;
    public final ModelRenderer mane;
    public final ModelRenderer leftEar;
    public final ModelRenderer rightEar;
    public final ModelRenderer tail;
    public final ModelRenderer frontLeftLeg;
    public final ModelRenderer frontRightLeg;
    public final ModelRenderer backLeftLeg;
    public final ModelRenderer backRightLeg;
    public final ModelRenderer childFrontLeftLeg;
    public final ModelRenderer childFrontRightLeg;
    public final ModelRenderer childBackLeftLeg;
    public final ModelRenderer childBackRightLeg;
    public final ModelRenderer saddle;
    public final ModelRenderer headpiece;
    public final ModelRenderer noseband;
    public final ModelRenderer leftBit;
    public final ModelRenderer rightBit;
    public final ModelRenderer leftRein;
    public final ModelRenderer rightRein;
    public final ModelRenderer leftChest;
    public final ModelRenderer rightChest;
    private final boolean armor;
    private final boolean saddleLayer;
    private final ModelBase sourceModel;
    private final List<ModelRenderer> adultLegs;
    private final List<ModelRenderer> childLegs;

    public ModernHorseModel() { this(false, false); }

    public ModernHorseModel(boolean donkey, boolean armor) {
        this(donkey, armor, false);
    }

    public ModernHorseModel(boolean donkey, boolean armor, boolean saddleLayer) {
        this(donkey, armor, saddleLayer, null);
    }

    public ModernHorseModel(boolean donkey, boolean armor, boolean saddleLayer, ModelBase sourceModel) {
        this.armor = armor;
        this.saddleLayer = saddleLayer;
        this.sourceModel = sourceModel == null ? this : sourceModel;
        textureWidth = textureHeight = 64;
        body = box(0, 32, -5, -8, -17, 10, 10, 22, .05F, 0, 11, 5);
        neck = box(0, 35, -2.05F, -6, -2, 4, 12, 7, 0, 0, 4, -12);
        neck.rotateAngleX = (float) (Math.PI / 6);
        head = box(0, 13, -3, -11, -2, 6, 5, 7, 0, 0, 0, 0);
        mouth = box(0, 25, -2, -11, -7, 4, 5, 5, 0, 0, 0, 0);
        mane = box(56, 36, -1, -11, 5.01F, 2, 16, 2, 0, 0, 0, 0);
        neck.addChild(head); neck.addChild(mouth); neck.addChild(mane);
        if (donkey) {
            leftEar = box(0, 12, -1, -7, 0, 2, 7, 1, 0, 1.25F, -10, 4);
            rightEar = box(0, 12, -1, -7, 0, 2, 7, 1, 0, -1.25F, -10, 4);
            leftEar.rotateAngleX = rightEar.rotateAngleX = (float) (Math.PI / 12);
            leftEar.rotateAngleZ = (float) (Math.PI / 12);
            rightEar.rotateAngleZ = -leftEar.rotateAngleZ;
        } else {
            leftEar = box(19, 16, .55F, -13, 4, 2, 3, 1, -.001F, 0, 0, 0);
            rightEar = box(19, 16, -2.55F, -13, 4, 2, 3, 1, -.001F, 0, 0, 0);
        }
        head.addChild(leftEar); head.addChild(rightEar);
        tail = box(42, 36, -1.5F, 0, 0, 3, 14, 4, 0, 0, -5, 2);
        tail.rotateAngleX = (float) (Math.PI / 6);
        body.addChild(tail);
        frontLeftLeg = leg(true, true, false); frontRightLeg = leg(false, true, false);
        backLeftLeg = leg(true, false, false); backRightLeg = leg(false, false, false);
        childFrontLeftLeg = leg(true, true, true); childFrontRightLeg = leg(false, true, true);
        childBackLeftLeg = leg(true, false, true); childBackRightLeg = leg(false, false, true);
        adultLegs = List.of(frontLeftLeg, frontRightLeg, backLeftLeg, backRightLeg);
        childLegs = List.of(childFrontLeftLeg, childFrontRightLeg, childBackLeftLeg, childBackRightLeg);
        saddle = box(26, 0, -5, -8, -9, 10, 9, 9, .5F, 0, 0, 0);
        body.addChild(saddle);
        leftBit = box(29, 5, 2, -9, -6, 1, 2, 2, 0, 0, 0, 0);
        rightBit = box(29, 5, -3, -9, -6, 1, 2, 2, 0, 0, 0, 0);
        headpiece = box(1, 1, -3, -11, -1.9F, 6, 5, 6, .22F, 0, 0, 0);
        noseband = box(19, 0, -2, -11, -4, 4, 5, 2, .2F, 0, 0, 0);
        leftRein = box(32, 2, 3.1F, -6, -8, 0, 3, 16, 0, 0, 0, 0);
        rightRein = box(32, 2, -3.1F, -6, -8, 0, 3, 16, 0, 0, 0, 0);
        leftRein.rotateAngleX = rightRein.rotateAngleX = (float) (-Math.PI / 6);
        for (ModelRenderer part : List.of(leftBit, rightBit, headpiece, noseband, leftRein, rightRein)) neck.addChild(part);
        leftChest = box(26, 21, -4, 0, -2, 8, 8, 3, 0, 6, -8, 0);
        rightChest = box(26, 21, -4, 0, -2, 8, 8, 3, 0, -6, -8, 0);
        leftChest.rotateAngleY = (float) (-Math.PI / 2); rightChest.rotateAngleY = -leftChest.rotateAngleY;
        body.addChild(leftChest); body.addChild(rightChest);
        if (saddleLayer) {
            // Keep the animated parents for CEM replacements, but never redraw
            // the horse skin with the equipment texture.
            for (ModelRenderer part : List.of(body, neck, head, mouth, mane, leftEar, rightEar,
                    tail, leftChest, rightChest)) part.cubeList.clear();
            for (ModelRenderer part : adultLegs) part.cubeList.clear();
            for (ModelRenderer part : childLegs) part.cubeList.clear();
        }
    }

    private ModelRenderer box(int u, int v, float x, float y, float z, int w, int h, int d,
                              float dilation, float px, float py, float pz) {
        ModelRenderer part = new ModelRenderer(this, u, v);
        part.addBox(x, y, z, w, h, d, dilation);
        part.setRotationPoint(px, py, pz);
        return part;
    }

    private ModelRenderer leg(boolean left, boolean front, boolean child) {
        ModelRenderer part = new ModelRenderer(this, 48, 21);
        part.mirror = left;
        part.addBox(left ? -3 : -1, child ? -6.51F : -1.01F, front ? -1.9F : -1,
                4, child ? 22 : 11, 4);
        part.setRotationPoint(left ? 4 : -4, 14, front ? -12 : 7);
        return part;
    }

    @Override
    public void render(Entity entity, float swing, float speed, float age, float yaw, float pitch, float scale) {
        EntityHorse horse = (EntityHorse) entity;
        prepare(horse, swing, speed, age, yaw, pitch);
        boolean child = RenderContext.useLegacyChildTransform(!horse.isAdultHorse());
        GL11.glPushMatrix();
        try {
            if (child) {
                float headScale = 1.5F / 2.7272F;
                GL11.glScalef(headScale, headScale, headScale);
                GL11.glTranslatef(0, 16.2F * scale, 1.36F * scale);
            }
            neck.render(scale);
        } finally { GL11.glPopMatrix(); }
        GL11.glPushMatrix();
        try {
            if (child) { GL11.glScalef(.5F, .5F, .5F); GL11.glTranslatef(0, 20 * scale, 0); }
            body.render(scale);
            for (ModelRenderer leg : adultLegs) leg.render(scale);
            for (ModelRenderer leg : childLegs) leg.render(scale);
        } finally { GL11.glPopMatrix(); }
    }

    /** Modern hierarchy pose driven by the corresponding MITE horse state. */
    public void prepare(EntityHorse horse, float swing, float speed, float age, float yaw, float pitch) {
        float delta = ETFRenderContext.tickDelta(0);
        float headYaw = Math.max(-20, Math.min(20, yaw));
        float headPitch = (float) Math.toRadians(pitch);
        if (speed > .2F) headPitch += Math.cos(swing * .8F) * .15F * speed;
        float eat = horse.getGrassEatingAmount(delta);
        float stand = horse.getRearingAmount(delta);
        float mouthOpen = horse.func_110201_q(delta);
        float rest = 1 - Math.max(stand, eat);
        neck.rotateAngleX = stand * ((float) Math.PI / 12 + headPitch)
                + eat * (2.1816616F + (float) Math.sin(age) * .05F)
                + rest * ((float) Math.PI / 6 + headPitch + mouthOpen * (float) Math.sin(age) * .05F);
        neck.rotateAngleY = (stand + rest) * (float) Math.toRadians(headYaw);
        neck.rotationPointY = stand * -4 + eat * 11 + rest * 4;
        neck.rotationPointZ = stand * -4 + eat * -12 + rest * -12;
        body.rotationPointY = 11;
        body.rotateAngleX = stand * (float) (-Math.PI / 4);
        float walk = (float) Math.cos((horse.isInWater() ? .2F : 1) * swing * .6662F + Math.PI);
        float walkFront = walk * .8F * speed;
        float rearingWalk = (float) Math.cos(age * .6F + Math.PI);
        frontLeftLeg.rotationPointY = frontRightLeg.rotationPointY = 2 * stand + 14 * (1 - stand);
        frontLeftLeg.rotationPointZ = frontRightLeg.rotationPointZ = -6 * stand - 10 * (1 - stand);
        frontLeftLeg.rotateAngleX = ((float) -Math.PI / 3 + rearingWalk) * stand + walkFront * (1 - stand);
        frontRightLeg.rotateAngleX = ((float) -Math.PI / 3 - rearingWalk) * stand - walkFront * (1 - stand);
        backLeftLeg.rotateAngleX = (float) Math.PI / 12 * stand - walk * .5F * speed * (1 - stand);
        backRightLeg.rotateAngleX = (float) Math.PI / 12 * stand + walk * .5F * speed * (1 - stand);
        tail.rotateAngleX = (float) Math.PI / 6 + speed * .75F;
        tail.rotationPointY = -5 + speed; tail.rotationPointZ = 2 + speed * 2;
        tail.rotateAngleY = horse.field_110278_bp != 0 ? (float) Math.cos(age * .7F) : 0;
        boolean child = !horse.isAdultHorse();
        for (int i = 0; i < adultLegs.size(); i++) {
            ModelRenderer adult = adultLegs.get(i), baby = childLegs.get(i);
            baby.rotationPointY = adult.rotationPointY; baby.rotationPointZ = adult.rotationPointZ;
            baby.rotateAngleX = adult.rotateAngleX;
            adult.showModel = !child; baby.showModel = child;
        }
        boolean tack = !armor && !child && horse.isHorseSaddled()
                && (saddleLayer || !EMFManager.INSTANCE.hasHorseSaddleModel(horse, sourceModel));
        for (ModelRenderer part : List.of(saddle, headpiece, noseband, leftBit, rightBit))
            part.showModel = tack;
        leftRein.showModel = rightRein.showModel = tack && horse.riddenByEntity != null;
        leftChest.showModel = rightChest.showModel = !armor && !saddleLayer && horse.isChested()
                && (horse.getHorseType() == 1 || horse.getHorseType() == 2);
    }
}
