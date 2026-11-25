package traben.entity_model_features.compat;

import dev.tr7zw.waveycapes.mite.api.WaveyCapesApi;

public final class WaveyCapesCompat {

    public static boolean shouldReplaceVanillaCape() {
        return WaveyCapesApi.shouldReplaceVanillaCape();
    }
}
