package traben.entity_model_features.model;

import net.minecraft.ModelBase;
import net.minecraft.ModelBox;
import net.minecraft.ModelRenderer;
import traben.entity_model_features.config.EMFConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** Per-resource virtual nodes: never changes the shared vanilla hierarchy or its display lists. */
public final class ModelStructure {

    private final Map<ModelRenderer, String> parts;
    private final Map<String, ModelRenderer> aliases = new LinkedHashMap<>();
    private final Map<ModelRenderer, List<ModelRenderer>> children = new IdentityHashMap<>();
    private final Map<ModelRenderer, List<ModelBox>> splitBoxes = new IdentityHashMap<>();
    private final Map<ModelRenderer, List<Integer>> extractedBoxes = new IdentityHashMap<>();
    private final ModelBase virtualOwner = new ModelBase() { };
    private final Set<ModelRenderer> virtualParts = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ModelRenderer> reparented = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ModelRenderer> childParts = Collections.newSetFromMap(new IdentityHashMap<>());

    ModelStructure(ModelBase model, Map<ModelRenderer, String> nativeParts) {
        parts = new LinkedHashMap<>(nativeParts);
        String type = model.getClass().getSimpleName();
        if (model instanceof ModernHorseModel) {
            aliases.put("left_front_leg", find("front_left_leg"));
            aliases.put("right_front_leg", find("front_right_leg"));
            aliases.put("left_hind_leg", find("back_left_leg"));
            aliases.put("right_hind_leg", find("back_right_leg"));
            aliases.put("head_saddle", find("headpiece"));
            aliases.put("mouth_saddle_wrap", find("noseband"));
            aliases.put("left_saddle_mouth", find("left_bit"));
            aliases.put("right_saddle_mouth", find("right_bit"));
            aliases.put("left_saddle_line", find("left_rein"));
            aliases.put("right_saddle_line", find("right_rein"));
        }
        if (type.equals("ModelArrow")) {
            // Legacy OptiFine arrow files used body as the root; accept both
            // spellings without duplicating or reparenting the native node.
            aliases.put("root", find("body"));
        }
        if (model instanceof ModernChestModel chest && chest.doubleChest) {
            String side = chest.left ? "right" : "left";
            String other = chest.left ? "left" : "right";
            for (String part : new String[]{"base", "lid", "knob"}) {
                aliases.put(part + "_" + side, find(part));
                if (!EMFConfig.get().doubleChestAnimFix) continue;
                ModelRenderer empty = new ModelRenderer(virtualOwner, part + "_" + other);
                virtualParts.add(empty);
                parts.put(empty, part + "_" + other);
            }
        }
        if (type.equals("ModelVillager") || type.equals("ModelWitch")) {
            ModelRenderer headwear = add("headwear", "head");
            if (find("headwear2") == null) add("headwear2", "headwear");
            else {
                ModelRenderer hat = find("headwear2");
                reparented.add(hat);
                children.computeIfAbsent(headwear, ignored -> new ArrayList<>()).add(hat);
            }
            split(add("bodywear", "body"), find("body"), 1);
        }
        if (type.equals("ModelBat")) split(add("feet", "body"), find("body"), 1);
        if (type.equals("ModelZombie") || type.equals("ModelBiped")) {
            add("left_ear", "head"); add("right_ear", "head");
            add("jacket", "body"); add("left_sleeve", "left_arm"); add("right_sleeve", "right_arm");
            add("left_pants", "left_leg"); add("right_pants", "right_leg");
        }
        parts.keySet().forEach(parent -> childParts.addAll(allChildren(parent)));
    }

    public Map<ModelRenderer, String> parts() { return parts; }

    public Set<String> names() {
        Set<String> names = new HashSet<>(parts.values());
        names.addAll(aliases.keySet());
        return names;
    }

    public ModelRenderer find(String name) {
        if (aliases.containsKey(name)) return aliases.get(name);
        for (var entry : parts.entrySet()) if (entry.getValue().equals(name)) return entry.getKey();
        return null;
    }

    public String canonicalName(String name) {
        ModelRenderer part = find(name);
        return part == null ? name : parts.get(part);
    }

    public List<ModelRenderer> children(ModelRenderer parent) { return children.getOrDefault(parent, List.of()); }

    public boolean hasChildren(ModelRenderer parent) { return children.containsKey(parent); }

    public boolean hasSplitBoxes(ModelRenderer parent) { return extractedBoxes.containsKey(parent); }

    public boolean isVirtual(ModelRenderer part) { return virtualParts.contains(part); }

    boolean isChildPart(ModelRenderer part) {
        return childParts.contains(part);
    }

    public List<ModelRenderer> allChildren(ModelRenderer parent) {
        List<ModelRenderer> result = new ArrayList<>();
        if (parent.childModels != null) for (Object child : parent.childModels)
            if (!reparented.contains((ModelRenderer) child)) result.add((ModelRenderer) child);
        result.addAll(children(parent));
        return result;
    }

    public List<ModelBox> boxes(ModelRenderer part) {
        List<ModelBox> virtual = splitBoxes.get(part);
        if (virtual != null) return virtual;
        List<Integer> removed = extractedBoxes.get(part);
        List<ModelBox> boxes = new ArrayList<>();
        for (int i = 0; i < part.cubeList.size(); i++)
            if (removed == null || !removed.contains(i)) boxes.add((ModelBox) part.cubeList.get(i));
        return boxes;
    }

    private ModelRenderer add(String name, String parentName) {
        ModelRenderer existing = find(name);
        if (existing != null) return existing;
        ModelRenderer parent = find(parentName);
        if (parent == null) throw new IllegalArgumentException("No parent " + parentName + " for " + name);
        ModelRenderer node = new ModelRenderer(virtualOwner, name);
        virtualParts.add(node);
        parts.put(node, name);
        children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(node);
        return node;
    }

    private void split(ModelRenderer virtual, ModelRenderer nativePart, int index) {
        if (nativePart.cubeList.size() <= index) return;
        splitBoxes.put(virtual, List.of((ModelBox) nativePart.cubeList.get(index)));
        extractedBoxes.computeIfAbsent(nativePart, ignored -> new ArrayList<>()).add(index);
    }
}
