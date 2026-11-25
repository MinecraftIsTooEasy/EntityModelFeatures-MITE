package traben.entity_model_features.model;

import net.minecraft.DynamicTexture;
import net.minecraft.Entity;
import net.minecraft.Minecraft;
import net.minecraft.ModelChest;
import net.minecraft.ResourceLocation;
import net.minecraft.TextureManager;
import net.minecraft.TileEntityChest;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.EMFManager;
import traben.entity_texture_features.features.ETFManager;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_texture_features.features.ETFTileEntity;
import traben.entity_texture_features.features.player.GeneratedTextureAccess;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class DoubleChestBridge {

    private record Pair(ModernChestModel right, ModernChestModel left) { }
    private record Key(ResourceLocation legacy, ResourceLocation emissive, boolean left, boolean doubleChest) { }
    private record Texture(ResourceLocation base, ResourceLocation emissive) { }
    private static final Map<Object, Pair> MODELS = new WeakHashMap<>();
    private static final Map<Object, ModernChestModel> SINGLE_MODELS = new WeakHashMap<>();
    private static final Map<Key, Texture> TEXTURES = new HashMap<>();
    private static long sequence;

    public static boolean render(Object renderer, ModelChest original, TileEntityChest tile, String name, float delta) {
        TileEntityChest neighbor = tile.adjacentChestXPos != null ? tile.adjacentChestXPos : tile.adjacentChestZPosition;
        if (neighbor == null) return false;
        ResourceLocation legacy = ETFRenderContext.originalTexture();
        if (legacy == null) return false;
        Entity rightEntity = ETFTileEntity.of(rightHalf(tile, neighbor));
        Entity leftEntity = ETFTileEntity.of(rightHalf(tile, neighbor) == tile ? neighbor : tile);
        ResourceLocation modernRight = modernTexture(legacy, false), modernLeft = modernTexture(legacy, true);
        boolean custom = EMFManager.INSTANCE.hasModel(name + "_right", rightEntity)
                || EMFManager.INSTANCE.hasModel(name + "_left", leftEntity);
        if (!custom && !ETFManager.INSTANCE.resourceExists(modernRight) && !ETFManager.INSTANCE.resourceExists(modernLeft))
            return false;
        ResourceLocation rightTexture = texture(rightEntity, legacy, modernRight, false, true);
        ResourceLocation leftTexture = texture(leftEntity, legacy, modernLeft, true, true);
        if (rightTexture == null || leftTexture == null) return false;
        Pair models = MODELS.computeIfAbsent(renderer, ignored -> new Pair(new ModernChestModel(false), new ModernChestModel(true)));
        float angle = original.chestLid.rotateAngleX;
        GL11.glPushMatrix();
        try {
            // Undo the old renderer's chest-local conversion; C is its own inverse.
            GL11.glTranslatef(0, 1, 1);
            GL11.glScalef(1, -1, -1);
            renderHalf(renderer, models.right, rightEntity, name + "_right", rightTexture, angle, delta);
            GL11.glTranslatef(1, 0, 0);
            renderHalf(renderer, models.left, leftEntity, name + "_left", leftTexture, angle, delta);
        } finally {
            GL11.glPopMatrix();
            ResourceLocation restore = ETFRenderContext.currentTexture();
            if (restore != null) Minecraft.getMinecraft().getTextureManager().bindTexture(restore);
        }
        return true;
    }

    public static boolean renderSingle(Object renderer, ModelChest original, Entity entity, String name, float delta) {
        ResourceLocation legacy = ETFRenderContext.originalTexture();
        if (legacy == null) return false;
        ResourceLocation selected = ETFManager.INSTANCE.getVariantTexture(entity, legacy);
        if (!EMFManager.INSTANCE.hasModel(name, entity) && ETFManager.INSTANCE.packFormat(selected) < 5) return false;
        ResourceLocation texture = texture(entity, legacy, legacy, false, false);
        if (texture == null) return false;
        ModernChestModel model = SINGLE_MODELS.computeIfAbsent(renderer, ignored -> new ModernChestModel());
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0, 1, 1);
            GL11.glScalef(1, -1, -1);
            renderHalf(renderer, model, entity, name, texture, original.chestLid.rotateAngleX, delta);
        } finally {
            GL11.glPopMatrix();
            ResourceLocation restore = ETFRenderContext.currentTexture();
            if (restore != null) Minecraft.getMinecraft().getTextureManager().bindTexture(restore);
        }
        return true;
    }

    public static TileEntityChest rightHalf(TileEntityChest tile, TileEntityChest neighbor) {
        int facing = tile.getWorldObj() == null ? 0 : tile.getBlockMetadata();
        return facing == 2 || facing == 5 ? neighbor : tile;
    }

    private static void renderHalf(Object renderer, ModernChestModel model, Entity entity, String name,
                                   ResourceLocation texture, float angle, float delta) {
        model.chestLid.rotateAngleX = angle;
        ETFRenderContext.push(entity, delta);
        try {
            ETFRenderContext.setCurrentTexture(texture);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            RenderContext.beginIsolatedNamed(renderer, model, entity, name, 0, 0,
                    entity.worldObj == null ? 0 : entity.worldObj.getTotalWorldTime() + delta, 0, 0, .0625F);
            try { model.renderAll(); }
            finally { RenderContext.end(); }
        } finally { ETFRenderContext.pop(); }
    }

    private static ResourceLocation modernTexture(ResourceLocation legacy, boolean left) {
        String path = legacy.getResourcePath();
        String family = path.contains("trapped") ? "trapped" : path.contains("christmas") ? "christmas" : "normal";
        return new ResourceLocation(legacy.getResourceDomain(), "textures/entity/chest/" + family + (left ? "_left.png" : "_right.png"), false);
    }

    private static ResourceLocation texture(Entity entity, ResourceLocation legacy, ResourceLocation modern,
                                             boolean left, boolean doubleChest) {
        ETFManager manager = ETFManager.INSTANCE;
        if (doubleChest && manager.resourceExists(modern)) return manager.getVariantTexture(entity, modern);
        ResourceLocation selected = manager.getVariantTexture(entity, legacy);
        if (!doubleChest && manager.packFormat(selected) >= 5) return selected;
        ResourceLocation emissive = manager.getEmissiveTextureForSelected(entity, selected);
        Key key = new Key(selected, emissive, left, doubleChest);
        Texture cached = TEXTURES.get(key);
        if (cached != null) return cached.base;
        try {
            BufferedImage image = read(selected);
            if (image == null) return null;
            BufferedImage converted = doubleChest ? ChestAtlas.split(image, left) : ChestAtlas.single(image);
            BufferedImage glow = emissive == null ? null : read(emissive);
            BufferedImage convertedEmissive = glow == null ? null : doubleChest ? ChestAtlas.split(glow, left) : ChestAtlas.single(glow);
            long id = sequence++;
            ResourceLocation base = generated(id, left, "base");
            ResourceLocation convertedGlow = glow == null ? null : generated(id, left, "emissive");
            TextureManager textures = Minecraft.getMinecraft().getTextureManager();
            textures.loadTexture(base, new DynamicTexture(converted));
            if (glow != null) textures.loadTexture(convertedGlow, new DynamicTexture(convertedEmissive));
            TEXTURES.put(key, new Texture(base, convertedGlow));
            manager.registerGeneratedTexture(base, convertedGlow);
            return base;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("[EMF] Cannot split chest texture " + selected + ": " + exception.getMessage());
            return null;
        }
    }

    private static BufferedImage read(ResourceLocation location) throws IOException {
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            return ImageIO.read(stream);
        }
    }

    private static ResourceLocation generated(long id, boolean left, String name) {
        return new ResourceLocation("entity_model_features", "chest/" + id + (left ? "/left/" : "/right/") + name + ".png", false);
    }

    public static void clear() {
        if (!TEXTURES.isEmpty()) {
            TextureManager manager = Minecraft.getMinecraft().getTextureManager();
            TEXTURES.values().forEach(texture -> {
                dispose(manager, texture.base);
                if (texture.emissive != null) dispose(manager, texture.emissive);
                ETFManager.INSTANCE.removeGeneratedTexture(texture.base);
            });
        }
        TEXTURES.clear();
        MODELS.clear();
        SINGLE_MODELS.clear();
    }

    private static void dispose(TextureManager manager, ResourceLocation location) {
        Object texture = ((GeneratedTextureAccess) (Object) manager).etf$removeGeneratedTexture(location);
        if (texture instanceof DynamicTexture dynamic) GL11.glDeleteTextures(dynamic.getGlTextureId());
    }
}
