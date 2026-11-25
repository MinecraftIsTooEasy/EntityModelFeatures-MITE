package traben.entity_model_features;

import net.fabricmc.api.ModInitializer;
import net.xiaoyu233.fml.ModResourceManager;
import moddedmite.rustedironcore.api.util.FabricUtil;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_model_features.config.EMFConfigOptions;
import traben.entity_texture_features.config.screens.FeatureConfigScreens;

public final class EMFInit implements ModInitializer {

    public static final String MOD_ID = "entity_model_features";

    @Override
    public void onInitialize() {
        ModResourceManager.addResourcePackDomain(MOD_ID);
        EMFConfig.FILE.load(FabricUtil.getConfigDirectory().resolve(MOD_ID + ".json"));
        FeatureConfigScreens.register("config.entity_model_features.title",
                parent -> FeatureConfigScreens.settings(parent, MOD_ID, EMFConfig.FILE, EMFConfigOptions.OPTIONS));
    }
}
