package traben.entity_model_features.model;

import net.minecraft.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JemPart {

    public String texture = "";
    public int[] textureSize;
    public String invertAxis = "";
    public float[] translate;
    public float[] rotate;
    public String mirrorTexture = "";
    public List<JemBox> boxes = new ArrayList<>();
    public List<JemBox> sprites = new ArrayList<>();
    public JemPart submodel;
    public List<JemPart> submodels = new ArrayList<>();
    public String baseId = "";
    public String model = "";
    public String id = "";
    public String part;
    public boolean attach;
    public float scale = 1.0F;
    public Map<String, float[]> attachments = new LinkedHashMap<>();
    public List<LinkedHashMap<String, String>> animations = new ArrayList<>();
    public transient ResourceLocation customTexture;

    public void inheritMissing(JemPart source) {
        if (texture.isEmpty()) texture = source.texture;
        if (textureSize == null) textureSize = source.textureSize;
        if (invertAxis.isEmpty()) invertAxis = source.invertAxis;
        if (translate == null) translate = source.translate;
        if (rotate == null) rotate = source.rotate;
        if (mirrorTexture.isEmpty()) mirrorTexture = source.mirrorTexture;
        if (boxes.isEmpty()) boxes = source.boxes;
        if (sprites.isEmpty()) sprites = source.sprites;
        if (submodel == null) submodel = source.submodel;
        if (submodels.isEmpty()) submodels = source.submodels;
        if (scale == 1.0F) scale = source.scale;
        if (attachments.isEmpty()) attachments = source.attachments;
        if (animations.isEmpty()) animations = source.animations;
        if (customTexture == null) customTexture = source.customTexture;
    }
}
