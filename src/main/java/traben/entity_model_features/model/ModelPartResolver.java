package traben.entity_model_features.model;

import net.minecraft.ModelBase;
import net.minecraft.ModelRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

public final class ModelPartResolver {

    static Map<ModelRenderer, String> resolve(ModelBase model) {
        Map<ModelRenderer, String> result = new LinkedHashMap<>();
        if (model instanceof SplitCrystalModel crystal) result.put(crystal.emf$getInnerGlass(), "inner_glass");
        Class<?> type = model.getClass();
        while (type != null && ModelBase.class.isAssignableFrom(type)) {
            Field[] fields = type.getDeclaredFields();
            Arrays.sort(fields, Comparator.comparingInt(ModelPartResolver::fieldPriority).thenComparing(Field::getName));
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelRenderer renderer) {
                        result.putIfAbsent(renderer, mapName(type.getSimpleName(), field.getName()));
                    } else if (value instanceof ModelRenderer[] renderers) {
                        String base = mapArrayName(type.getSimpleName(), field.getName());
                        for (int i = 0; i < renderers.length; i++) {
                            if (renderers[i] != null) {
                                String name = (type.getSimpleName().equals("ModelBoat") || type.getSimpleName().equals("ModelMinecart")) && i < 5
                                        ? new String[]{"bottom", "back", "front", "right", "left"}[i] : base + (i + 1);
                                result.putIfAbsent(renderers[i], name);
                            }
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        for (Object entry : model.boxList) {
            if (entry instanceof ModelRenderer renderer && renderer.boxName != null && !renderer.boxName.isEmpty()) {
                result.putIfAbsent(renderer, normalize(renderer.boxName));
            }
        }
        return result;
    }

    public static Set<String> resolvedNames(ModelBase model) {
        if (model.getClass().getSimpleName().equals("ModelHorse")) model = new ModernHorseModel();
        return new ModelStructure(model, resolve(model)).names();
    }

    private static int fieldPriority(Field field) {
        String name = field.getName();
        if (name.endsWith("Shin") || name.endsWith("Hoof") || name.equals("tailMiddle") || name.equals("tailTip")) return 2;
        return 0;
    }

    private static String mapName(String model, String field) {
        String key = model + "." + field;
        String mapped = switch (key) {
            case "ModernPlayerModel.jacket" -> "jacket";
            case "ModernPlayerModel.leftSleeve" -> "left_sleeve";
            case "ModernPlayerModel.rightSleeve" -> "right_sleeve";
            case "ModernPlayerModel.leftPants" -> "left_pants";
            case "ModernPlayerModel.rightPants" -> "right_pants";
            case "ModelSkeletonHead.skeletonHead" -> "head";
            case "ModelChest.chestLid" -> "lid";
            case "ModelChest.chestBelow" -> "base";
            case "ModelChest.chestKnob" -> "knob";
            case "ModelSign.signBoard" -> "board";
            case "ModelSign.signStick" -> "stick";
            case "ModelBiped.bipedCloak" -> "cape";
            case "ModelBiped.bipedEars" -> "ear";
            case "ModelEnderCrystal.cube" -> "cube";
            case "ModelEnderCrystal.glass" -> "outer_glass";
            case "ModelCreeper.field_78133_b" -> "headwear";
            case "ModelLeashKnot.field_110723_a" -> "knot";
            case "ModelMagmaCube.field_78108_b" -> "core";
            case "ModelGelatinousCube.bodies", "ModelSlime.slimeBodies" -> "body";
            case "ModelGelatinousCube.right_eye", "ModelSlime.slimeRightEye" -> "right_eye";
            case "ModelGelatinousCube.left_eye", "ModelSlime.slimeLeftEye" -> "left_eye";
            case "ModelGelatinousCube.mouth", "ModelSlime.slimeMouth" -> "mouth";
            case "ModelVillager.rightVillagerLeg" -> "right_leg";
            case "ModelVillager.leftVillagerLeg" -> "left_leg";
            case "ModelWitch.field_82901_h" -> "mole";
            case "ModelWitch.witchHat" -> "headwear2";
            case "ModelSnowMan.bottomBody" -> "body_bottom";
            case "ModelHorse.mouthTop", "ModelHorse.mouthBottom" -> "mouth";
            case "ModelHorse.tailBase", "ModelHorse.tailMiddle", "ModelHorse.tailTip" -> "tail";
            case "ModelHorse.backLeftLeg", "ModelHorse.backLeftShin", "ModelHorse.backLeftHoof" -> "back_left_leg";
            case "ModelHorse.backRightLeg", "ModelHorse.backRightShin", "ModelHorse.backRightHoof" -> "back_right_leg";
            case "ModelHorse.frontRightLeg", "ModelHorse.frontLeftShin", "ModelHorse.frontLeftHoof" -> "front_left_leg";
            case "ModelHorse.field_110684_D", "ModelHorse.frontRightShin", "ModelHorse.frontRightHoof" -> "front_right_leg";
            case "ModelHorse.field_110687_G" -> "left_chest";
            case "ModelHorse.field_110695_H" -> "right_chest";
            case "ModelHorse.field_110696_I", "ModelHorse.field_110697_J", "ModelHorse.field_110698_K",
                    "ModelHorse.field_110691_L", "ModelHorse.field_110692_M",
                    "ModelHorse.field_110693_N", "ModelHorse.field_110694_O" -> "saddle";
            case "ModelHorse.field_110700_P" -> "left_bit";
            case "ModelHorse.field_110699_Q" -> "right_bit";
            case "ModelHorse.field_110702_R" -> "left_rein";
            case "ModelHorse.field_110701_S" -> "right_rein";
            case "ModelHorse.field_110717_i" -> "headpiece";
            default -> null;
        };
        if (mapped != null) {
            return mapped;
        }
        String value = field;
        for (String prefix : new String[]{"biped", "spider", "wolf", "villager", "ironGolem", "snowman", "ocelot", "bat", "blaze", "squid", "slime", "witch", "horse"}) {
            if (value.startsWith(prefix) && value.length() > prefix.length()) {
                value = value.substring(prefix.length());
                break;
            }
        }
        value = value.replace("Main", "");
        return normalize(value);
    }

    private static String mapArrayName(String model, String field) {
        String key = model + "." + field;
        return switch (key) {
            case "ModelBlaze.blazeSticks" -> "stick";
            case "ModelGhast.tentacles", "ModelSquid.squidTentacles" -> "tentacle";
            case "ModelSilverfish.silverfishBodyParts" -> "body";
            case "ModelSilverfish.silverfishWings" -> "wing";
            case "ModelMagmaCube.field_78109_a" -> "segment";
            case "ModelWither.field_82905_a" -> "head";
            case "ModelWither.field_82904_b" -> "body";
            default -> normalize(field).replace("_parts", "").replace("sides", "side");
        };
    }

    private static String normalize(String name) {
        String withUnderscores = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return withUnderscores.toLowerCase(Locale.ROOT);
    }
}
