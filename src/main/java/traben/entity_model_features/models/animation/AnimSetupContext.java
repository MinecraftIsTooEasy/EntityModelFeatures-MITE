package traben.entity_model_features.models.animation;

import net.minecraft.ModelRenderer;
import traben.entity_model_features.model.CustomModel;
import java.util.HashMap;

/** Compilation context using MITE parts. CustomPart values retain their real render identity. */
public final class AnimSetupContext implements AutoCloseable {

    public CustomModel oldAnimationHandler;
    public HashMap<String, Object> allPartsBySingleAndFullHeirachicalId;
    public String animKey;
    public final String modelName;

    public AnimSetupContext(String modelName, CustomModel model, HashMap<String, Object> parts) {
        this.modelName = modelName;
        oldAnimationHandler = model;
        allPartsBySingleAndFullHeirachicalId = parts;
    }

    public ModelRenderer vanillaPart(String key) {
        Object part = allPartsBySingleAndFullHeirachicalId.get(key);
        return part instanceof ModelRenderer renderer ? renderer : null;
    }

    @Override
    public void close() {
        oldAnimationHandler = null;
        allPartsBySingleAndFullHeirachicalId = null;
    }
}
