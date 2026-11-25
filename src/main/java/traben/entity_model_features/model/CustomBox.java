package traben.entity_model_features.model;

import net.minecraft.PositionTextureVertex;
import net.minecraft.Tessellator;
import net.minecraft.TexturedQuad;
import traben.entity_model_features.config.EMFConfig;

import java.util.ArrayList;
import java.util.List;

final class CustomBox {

    private final List<TexturedQuad> faces = new ArrayList<>(6);

    CustomBox(JemBox box, String invertAxis, String mirrorTexture, float textureWidth, float textureHeight) {
        if (box.coordinates.length != 6) {
            return;
        }
        boolean invertX = invertAxis.contains("x");
        boolean invertY = invertAxis.contains("y");
        boolean invertZ = invertAxis.contains("z");
        float x = box.coordinates[0];
        float y = box.coordinates[1];
        float z = box.coordinates[2];
        float width = box.coordinates[3];
        float height = box.coordinates[4];
        float depth = box.coordinates[5];
        if (invertX) x = -x - width;
        if (invertY) y = -y - height;
        if (invertZ) z = -z - depth;
        boolean separateAdds = box.sizeAddX != 0.0F || box.sizeAddY != 0.0F || box.sizeAddZ != 0.0F;
        float addX = box.sizesAdd.length == 3 ? box.sizesAdd[0] : separateAdds ? box.sizeAddX : box.sizeAdd;
        float addY = box.sizesAdd.length == 3 ? box.sizesAdd[1] : separateAdds ? box.sizeAddY : box.sizeAdd;
        float addZ = box.sizesAdd.length == 3 ? box.sizesAdd[2] : separateAdds ? box.sizeAddZ : box.sizeAdd;
        float x1 = x - addX;
        float y1 = y - addY;
        float z1 = z - addZ;
        float x2 = x + width + addX;
        float y2 = y + height + addY;
        float z2 = z + depth + addZ;
        // Per-face UVs already describe their orientation; EMF only mirrors atlas-offset boxes.
        boolean mirrorU = box.textureOffset.length == 2 && mirrorTexture.contains("u");
        boolean mirrorV = box.textureOffset.length == 2 && mirrorTexture.contains("v");
        if (mirrorU) {
            float swap = x1;
            x1 = x2;
            x2 = swap;
        }
        PositionTextureVertex v000 = vertex(x1, y1, z1);
        PositionTextureVertex v100 = vertex(x2, y1, z1);
        PositionTextureVertex v110 = vertex(x2, y2, z1);
        PositionTextureVertex v010 = vertex(x1, y2, z1);
        PositionTextureVertex v001 = vertex(x1, y1, z2);
        PositionTextureVertex v101 = vertex(x2, y1, z2);
        PositionTextureVertex v111 = vertex(x2, y2, z2);
        PositionTextureVertex v011 = vertex(x1, y2, z2);

        if (box.textureOffset.length == 2) {
            float u = box.textureOffset[0];
            float v = box.textureOffset[1];
            float w = width;
            float h = height;
            float d = depth;
            addFace(new PositionTextureVertex[]{v101, v100, v110, v111}, u + d + w, v + d, u + d + w + d, v + d + h, textureWidth, textureHeight, mirrorU, mirrorV);
            addFace(new PositionTextureVertex[]{v000, v001, v011, v010}, u, v + d, u + d, v + d + h, textureWidth, textureHeight, mirrorU, mirrorV);
            addFace(new PositionTextureVertex[]{v101, v001, v000, v100}, u + d, v, u + d + w, v + d, textureWidth, textureHeight, mirrorU, mirrorV);
            addFace(new PositionTextureVertex[]{v110, v010, v011, v111}, u + d + w, v + d, u + d + w + w, v, textureWidth, textureHeight, mirrorU, mirrorV);
            addFace(new PositionTextureVertex[]{v100, v000, v010, v110}, u + d, v + d, u + d + w, v + d + h, textureWidth, textureHeight, mirrorU, mirrorV);
            addFace(new PositionTextureVertex[]{v001, v101, v111, v011}, u + d + w + d, v + d, u + d + w + d + w, v + d + h, textureWidth, textureHeight, mirrorU, mirrorV);
        } else {
            addUvFace(new PositionTextureVertex[]{v011, v111, v110, v010}, first(box.uvDown), textureWidth, textureHeight);
            addUvFace(new PositionTextureVertex[]{v000, v100, v101, v001}, first(box.uvUp), textureWidth, textureHeight);
            addUvFace(new PositionTextureVertex[]{v100, v000, v010, v110}, first(box.uvNorth, box.uvFront), textureWidth, textureHeight);
            addUvFace(new PositionTextureVertex[]{v001, v101, v111, v011}, first(box.uvSouth, box.uvBack), textureWidth, textureHeight);
            addUvFace(new PositionTextureVertex[]{v101, v100, v110, v111}, first(box.uvWest, box.uvLeft), textureWidth, textureHeight);
            addUvFace(new PositionTextureVertex[]{v000, v001, v011, v010}, first(box.uvEast, box.uvRight), textureWidth, textureHeight);
        }
    }

    void render(Tessellator tessellator, float scale) {
        for (TexturedQuad face : faces) {
            face.draw(tessellator, scale);
        }
    }

    private void addUvFace(PositionTextureVertex[] vertices, float[] uv, float textureWidth, float textureHeight) {
        if (uv.length != 4) {
            return;
        }
        TexturedQuad face = new TexturedQuad(vertices);
        float[] coordinates = uv.clone();
        if (EMFConfig.get().enforceOptiFineFloorUVs)
            for (int i = 0; i < 4; i++) coordinates[i] = (float) Math.floor(coordinates[i]);
        vertices[0] = vertices[0].setTexturePosition(coordinates[2] / textureWidth, coordinates[1] / textureHeight);
        vertices[1] = vertices[1].setTexturePosition(coordinates[0] / textureWidth, coordinates[1] / textureHeight);
        vertices[2] = vertices[2].setTexturePosition(coordinates[0] / textureWidth, coordinates[3] / textureHeight);
        vertices[3] = vertices[3].setTexturePosition(coordinates[2] / textureWidth, coordinates[3] / textureHeight);
        faces.add(face);
    }

    private void addFace(PositionTextureVertex[] vertices, float u1, float v1, float u2, float v2,
                         float textureWidth, float textureHeight, boolean mirrorU, boolean mirrorV) {
        if (mirrorV) {
            float swap = v1;
            v1 = v2;
            v2 = swap;
        }
        TexturedQuad face = new TexturedQuad(vertices);
        vertices[0] = vertices[0].setTexturePosition(u2 / textureWidth, v1 / textureHeight);
        vertices[1] = vertices[1].setTexturePosition(u1 / textureWidth, v1 / textureHeight);
        vertices[2] = vertices[2].setTexturePosition(u1 / textureWidth, v2 / textureHeight);
        vertices[3] = vertices[3].setTexturePosition(u2 / textureWidth, v2 / textureHeight);
        if (mirrorU) {
            face.flipFace();
        }
        faces.add(face);
    }

    private static PositionTextureVertex vertex(float x, float y, float z) {
        return new PositionTextureVertex(x, y, z, 0.0F, 0.0F);
    }

    private static float[] first(float[]... values) {
        for (float[] value : values) {
            if (value != null && value.length == 4) {
                return value;
            }
        }
        return new float[0];
    }
}
