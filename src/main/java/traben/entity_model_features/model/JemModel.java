package traben.entity_model_features.model;

import net.minecraft.ResourceLocation;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public final class JemModel {

    public String texture = "";
    public int[] textureSize = {64, 32};
    @SerializedName("shadow_size")
    public float shadowSize = 1.0F;
    public List<JemPart> models = new ArrayList<>();
    public transient ResourceLocation customTexture;
}
