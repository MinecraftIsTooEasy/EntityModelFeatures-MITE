package traben.entity_model_features.model;

import net.minecraft.Entity;
import net.minecraft.ResourceLocation;
import traben.entity_model_features.config.EMFConfig;
import traben.entity_texture_features.ETFApi;
import traben.entity_texture_features.ETFApi.ETFVariantSuffixProvider;
import traben.entity_texture_features.features.ETFManager;
import traben.entity_texture_features.config.ETFConfig;
import moddedmite.rustedironcore.api.entity.EntityIdentity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.BiFunction;
import java.util.ArrayList;

/** A directory is selected by owning pack first, then EMF and subfolder preference. */
public final class ModelResources {

    public record Selection(ResourceLocation file, int ruleIndex) { }

    public record Directory(ResourceLocation file, String family, String name, boolean nested, int rank) {

        public ResourceLocation relative(String fileName) {
            String path = file.getResourcePath();
            return new ResourceLocation(file.getResourceDomain(), path.substring(0, path.lastIndexOf('/') + 1) + fileName, false);
        }

        public boolean accepts(Directory other) {
            return other != null && nested == other.nested && rank <= other.rank;
        }
    }

    private record Group(Directory base, Directory context, ETFVariantSuffixProvider provider, List<Integer> suffixes) {

        int rank() { return base == null ? context.rank : base.rank; }
    }

    private final Map<String, Group> groups = new HashMap<>();
    private final ToIntFunction<ResourceLocation> resourceRank;
    private final BiFunction<ResourceLocation, ResourceLocation, ETFVariantSuffixProvider> providerFactory;

    public ModelResources() {
        this(resource -> ETFApi.resourceExists(resource) ? Math.max(0, ETFManager.INSTANCE.packRank(resource)) : -1);
    }

    public ModelResources(ToIntFunction<ResourceLocation> resourceRank) {
        this(resourceRank, (properties, original) -> ETFApi.getVariantSupplierOrNull(properties, original, "models"));
    }

    public ModelResources(ToIntFunction<ResourceLocation> resourceRank,
                          BiFunction<ResourceLocation, ResourceLocation, ETFVariantSuffixProvider> providerFactory) {
        this.resourceRank = resourceRank;
        this.providerFactory = providerFactory;
    }

    public void clear() { groups.clear(); }

    public Directory directory(String id, String suffix) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String name = colon < 0 ? id : id.substring(colon + 1);
        Directory best = null;
        // On equal pack ranks: EMF > OptiFine > MCPatcher; subfolder > top level.
        for (String family : List.of("mcpatcher", "optifine", "emf")) {
            for (boolean nested : new boolean[]{false, true}) {
                ResourceLocation file = new ResourceLocation(namespace, family + "/cem/" +
                        (nested ? name + "/" : "") + name + suffix, false);
                int rank = resourceRank.applyAsInt(file);
                if (rank >= 0 && (best == null || rank >= best.rank)) best = new Directory(file, family, name, nested, rank);
            }
        }
        return best;
    }

    public ResourceLocation select(String name, List<String> fallbacks, Entity entity) {
        return selection(name, fallbacks, entity).file;
    }

    public Selection selection(String name, List<String> fallbacks, Entity entity) {
        Group best = bestGroup(name, fallbacks);
        if (best == null) return new Selection(null, 0);
        int suffix = 1;
        if (entity != null) {
            if (best.provider != null) suffix = best.provider.getSuffixForEntity(entity);
            else if (!best.suffixes.isEmpty()) {
                long seed = EntityIdentity.uuid(entity).getLeastSignificantBits() & 0x7FFFFFFFL;
                suffix = best.suffixes.get((int) (seed % best.suffixes.size()));
            }
        }
        int rule = entity == null || best.provider == null ? 0 : best.provider.getLastRuleIndex(EntityIdentity.uuid(entity));
        return new Selection(selectedFile(best, suffix), rule);
    }

    public ResourceLocation selectSuffix(String name, List<String> fallbacks, int suffix) {
        Group best = bestGroup(name, fallbacks);
        return best == null ? null : selectedFile(best, suffix);
    }

    private Group bestGroup(String name, List<String> fallbacks) {
        Group best = group(name, fallbacks.isEmpty() ? name : fallbacks.get(0));
        for (String fallback : fallbacks) {
            Group candidate = group(fallback, fallback);
            if (candidate != null && (best == null || candidate.rank() > best.rank())) best = candidate;
        }
        return best;
    }

    private ResourceLocation selectedFile(Group best, int suffix) {
        if (suffix > 1) {
            String id = best.context.file.getResourceDomain() + ":" + best.context.name;
            Directory variant = directory(id, suffix + ".jem");
            if (best.context.accepts(variant)) return variant.file;
        }
        return best.base == null ? null : best.base.file;
    }

    public boolean exists(String name, List<String> fallbacks) {
        if (group(name, fallbacks.isEmpty() ? name : fallbacks.get(0)) != null) return true;
        return fallbacks.stream().anyMatch(fallback -> group(fallback, fallback) != null);
    }

    private Group group(String id, String fallbackProperties) {
        String key = id + "\u0000" + fallbackProperties;
        if (groups.containsKey(key)) return groups.get(key);
        Directory base = directory(id, ".jem");
        Directory variations = directory(id, ".properties");
        if (variations == null) variations = directory(id, "2.jem");
        if (base != null && !base.accepts(variations)) variations = null;
        Directory context = base == null ? variations : base;
        Group result = null;
        EMFConfig config = EMFConfig.get();
        if (context != null && !(base == null && config.enforceOptifineVariationRequiresDefaultModel_v2)
                && !(base != null && base.nested && variations == null && config.enforceOptifineSubFoldersVariantOnly)) {
            ResourceLocation properties = variations != null && variations.file.getResourcePath().endsWith(".properties")
                    ? variations.file : context.relative(context.name + ".properties");
            // A matching EMF directory may override an OptiFine properties file, as upstream does.
            if (!context.family.equals("emf")) {
                ResourceLocation override = new ResourceLocation(properties.getResourceDomain(),
                        properties.getResourcePath().replace(context.family + "/cem/", "emf/cem/"), false);
                if (resourceRank.applyAsInt(override) >= 0) properties = override;
            }
            if (resourceRank.applyAsInt(properties) < 0 && config.allowOptifineFallbackProperties) {
                String fallbackName = fallbackProperties.substring(fallbackProperties.indexOf(':') + 1);
                properties = context.relative(fallbackName + ".properties");
            }
            if (resourceRank.applyAsInt(properties) < context.rank) properties = null;
            ETFVariantSuffixProvider provider = variations == null || properties == null ? null :
                    providerFactory.apply(properties, variations.relative(context.name + ".jem"));
            List<Integer> suffixes = new ArrayList<>();
            if (provider == null && variations != null) {
                suffixes.add(1);
                for (int suffix = 2; suffix < suffixes.size() + 10; suffix++) {
                    if (context.accepts(directory(id, suffix + ".jem"))) suffixes.add(suffix);
                    else if (!ETFConfig.get().optifine_allowWeirdSkipsInTrueRandom) break;
                }
            }
            result = new Group(base, context, provider, List.copyOf(suffixes));
        }
        groups.put(key, result);
        return result;
    }
}
