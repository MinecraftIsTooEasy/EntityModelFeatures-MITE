package traben.entity_model_features.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.Minecraft;
import net.minecraft.ModelBase;
import net.minecraft.ModelBox;
import net.minecraft.ModelRenderer;
import net.minecraft.PositionTextureVertex;
import net.minecraft.TexturedQuad;
import traben.entity_model_features.config.EMFConfig;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exports the actual legacy cuboids as an editable OptiFine-compatible JEM. */
public final class ModelExporter {

    private record BoxData(JsonObject json, boolean mirrored) { }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Field QUADS = field(ModelBox.class, "quadList");
    private static final Set<String> EXPORTED = new java.util.HashSet<>();

    private ModelExporter() {
    }

    public static void clear() {
        EXPORTED.clear();
    }

    public static void export(ModelBase model, String modelName) {
        if (!EXPORTED.add(modelName + '\0' + model.getClass().getName())) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.mcDataDir == null) return;
        String namespace = "minecraft";
        String fileName = modelName;
        int colon = modelName.indexOf(':');
        if (colon > 0) {
            namespace = modelName.substring(0, colon);
            fileName = modelName.substring(colon + 1);
        }
        namespace = safePath(namespace);
        fileName = safePath(fileName);
        Path output = minecraft.mcDataDir.toPath().resolve("emf/export/assets")
                .resolve(namespace).resolve("optifine/cem").resolve(fileName + ".jem");
        try {
            Files.createDirectories(output.getParent());
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(model, modelName), writer);
            }
            System.out.println("[EMF] Exported model " + modelName + " to " + output);
        } catch (IOException | RuntimeException exception) {
            System.err.println("[EMF] Failed to export model " + modelName + ": " + exception.getMessage());
        }
    }

    static JsonObject toJson(ModelBase model, String modelName) {
        Map<ModelRenderer, String> resolved = new LinkedHashMap<>(ModelPartResolver.resolve(model));
        Set<ModelRenderer> mapped = Collections.newSetFromMap(new IdentityHashMap<>());
        mapped.addAll(resolved.keySet());
        JsonObject jem = new JsonObject();
        ModelRenderer first = resolved.keySet().stream().findFirst().orElse(null);
        jem.add("textureSize", array(first == null ? model.textureWidth : first.textureWidth,
                first == null ? model.textureHeight : first.textureHeight));
        JsonArray parts = new JsonArray();
        Map<String, Integer> ids = new LinkedHashMap<>();
        resolved.forEach((part, target) -> {
            int suffix = ids.merge(target, 1, Integer::sum);
            parts.add(topLevel(part, target, target + "_export" + (suffix == 1 ? "" : "_" + suffix), mapped));
        });
        jem.add("models", parts);
        return jem;
    }

    private static JsonObject topLevel(ModelRenderer part, String target, String id, Set<ModelRenderer> mapped) {
        float x = part.rotationPointX;
        float y = -24.0F + part.rotationPointY;
        float z = -part.rotationPointZ;
        JsonObject json = basePart(part, id);
        json.addProperty("part", target);
        json.addProperty("attach", false);
        addIfNonZero(json, "translate", x, y, z);
        if (EMFConfig.get().exportRotations) addRotation(json, part);
        addBoxes(json, boxes(part, x, y, z), id);
        addChildren(json, part, id, mapped);
        return json;
    }

    private static JsonObject child(ModelRenderer part, String id) {
        JsonObject json = basePart(part, id);
        addIfNonZero(json, "translate", -part.rotationPointX, -part.rotationPointY, part.rotationPointZ);
        addRotation(json, part);
        addBoxes(json, boxes(part, 0, 0, 0), id);
        if (part.childModels != null) {
            JsonArray children = new JsonArray();
            for (int index = 0; index < part.childModels.size(); index++) {
                children.add(child((ModelRenderer) part.childModels.get(index), id + "_child_" + (index + 1)));
            }
            if (children.size() > 0) json.add("submodels", children);
        }
        return json;
    }

    private static JsonObject basePart(ModelRenderer part, String id) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("invertAxis", "xy");
        json.add("textureSize", array(part.textureWidth, part.textureHeight));
        return json;
    }

    private static void addChildren(JsonObject json, ModelRenderer part, String id, Set<ModelRenderer> mapped) {
        if (part.childModels == null) return;
        JsonArray children = json.has("submodels") ? json.getAsJsonArray("submodels") : new JsonArray();
        for (int index = 0; index < part.childModels.size(); index++) {
            ModelRenderer candidate = (ModelRenderer) part.childModels.get(index);
            if (!mapped.contains(candidate)) children.add(child(candidate, id + "_child_" + (index + 1)));
        }
        if (children.size() > 0) json.add("submodels", children);
    }

    private static void addRotation(JsonObject json, ModelRenderer part) {
        float degrees = (float) (180.0D / Math.PI);
        addIfNonZero(json, "rotate", -part.rotateAngleX * degrees,
                -part.rotateAngleY * degrees, part.rotateAngleZ * degrees);
    }

    private static List<BoxData> boxes(ModelRenderer part, float x, float y, float z) {
        List<BoxData> result = new ArrayList<>();
        for (Object value : part.cubeList) {
            ModelBox box = (ModelBox) value;
            float width = box.posX2 - box.posX1;
            float height = box.posY2 - box.posY1;
            float depth = box.posZ2 - box.posZ1;
            TexturedQuad[] quads = quads(box);
            Bounds bounds = bounds(quads);
            JsonObject json = new JsonObject();
            json.add("coordinates", array(-box.posX1 - width - x, -box.posY1 - height - y,
                    box.posZ1 - z, width, height, depth));
            float addX = ((box.posX1 - bounds.minX) + (bounds.maxX - box.posX2)) * 0.5F;
            float addY = ((box.posY1 - bounds.minY) + (bounds.maxY - box.posY2)) * 0.5F;
            float addZ = ((box.posZ1 - bounds.minZ) + (bounds.maxZ - box.posZ2)) * 0.5F;
            if (same(addX, addY) && same(addX, addZ)) {
                if (!zero(addX)) json.addProperty("sizeAdd", clean(addX));
            } else {
                if (!zero(addX)) json.addProperty("sizeAddX", clean(addX));
                if (!zero(addY)) json.addProperty("sizeAddY", clean(addY));
                if (!zero(addZ)) json.addProperty("sizeAddZ", clean(addZ));
            }
            TexturedQuad left = quads[1];
            float u = Float.POSITIVE_INFINITY;
            float v = Float.POSITIVE_INFINITY;
            for (PositionTextureVertex vertex : left.vertexPositions) {
                u = Math.min(u, vertex.texturePositionX * part.textureWidth);
                v = Math.min(v, vertex.texturePositionY * part.textureHeight);
            }
            json.add("textureOffset", array(u, v - depth));
            float faceX = 0;
            for (PositionTextureVertex vertex : quads[0].vertexPositions) faceX += vertex.vector3D.xCoord;
            faceX /= quads[0].vertexPositions.length;
            boolean mirrored = Math.abs(faceX - bounds.minX) < Math.abs(faceX - bounds.maxX);
            result.add(new BoxData(json, mirrored));
        }
        return result;
    }

    private static void addBoxes(JsonObject part, List<BoxData> boxes, String id) {
        if (boxes.isEmpty()) return;
        boolean mirror = boxes.get(0).mirrored();
        boolean mixed = boxes.stream().anyMatch(box -> box.mirrored() != mirror);
        if (!mixed) {
            JsonArray values = new JsonArray();
            boxes.forEach(box -> values.add(box.json()));
            part.add("boxes", values);
            if (mirror) part.addProperty("mirrorTexture", "u");
            return;
        }
        JsonArray children = part.has("submodels") ? part.getAsJsonArray("submodels") : new JsonArray();
        for (int index = 0; index < boxes.size(); index++) {
            BoxData box = boxes.get(index);
            JsonObject child = new JsonObject();
            child.addProperty("id", id + "_box_" + (index + 1));
            child.addProperty("invertAxis", "xy");
            JsonArray values = new JsonArray();
            values.add(box.json());
            child.add("boxes", values);
            if (box.mirrored()) child.addProperty("mirrorTexture", "u");
            children.add(child);
        }
        part.add("submodels", children);
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) { }

    private static Bounds bounds(TexturedQuad[] quads) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (TexturedQuad quad : quads) for (PositionTextureVertex vertex : quad.vertexPositions) {
            float x = (float) vertex.vector3D.xCoord;
            float y = (float) vertex.vector3D.yCoord;
            float z = (float) vertex.vector3D.zCoord;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static TexturedQuad[] quads(ModelBox box) {
        try {
            return (TexturedQuad[]) QUADS.get(box);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read ModelBox quads", exception);
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void addIfNonZero(JsonObject json, String name, float x, float y, float z) {
        if (!zero(x) || !zero(y) || !zero(z)) json.add(name, array(x, y, z));
    }

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(new JsonPrimitive(clean(value)));
        return array;
    }

    private static float clean(float value) {
        float rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.0001F) return rounded;
        return value == -0.0F ? 0.0F : value;
    }

    private static boolean zero(float value) {
        return Math.abs(value) < 0.0001F;
    }

    private static boolean same(float first, float second) {
        return Math.abs(first - second) < 0.0001F;
    }

    private static String safePath(String value) {
        String cleaned = value.replace('\\', '/').replaceAll("[^a-zA-Z0-9_./-]", "_");
        while (cleaned.contains("..")) cleaned = cleaned.replace("..", "_");
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
