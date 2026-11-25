package traben.entity_model_features.model;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import traben.entity_model_features.config.EMFConfig;

import java.nio.FloatBuffer;

/** Geometry-only debug passes; never repeats model animation or renderer effects. */
public final class DebugGeometry {

    public static void draw(Runnable geometry, boolean custom) {
        if (RenderContext.currentModel() == null || RenderContext.isSuspended()) {
            geometry.run();
            return;
        }
        EMFConfig.RenderModeChoice mode = EMFConfig.get().getRenderModeFor(RenderContext.currentEntity());
        if (mode == EMFConfig.RenderModeChoice.NONE) return;
        if (mode == EMFConfig.RenderModeChoice.NORMAL) {
            geometry.run();
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_POLYGON_BIT
                | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            if (mode == EMFConfig.RenderModeChoice.GREEN) {
                FloatBuffer color = BufferUtils.createFloatBuffer(4);
                GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
                float flash = Math.abs((float) Math.sin(System.currentTimeMillis() / 1000.0));
                GL11.glColor4f(flash, color.get(1), flash, color.get(3));
                geometry.run();
                return;
            }
            if (mode != EMFConfig.RenderModeChoice.LINES) geometry.run();
            float alpha = mode == EMFConfig.RenderModeChoice.LINES_AND_TEXTURE_FLASH
                    ? ((float) Math.sin(System.currentTimeMillis() / 1000.0) + 1) / 2 : 1;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_LINE);
            GL11.glPolygonOffset(-1, -1);
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            GL11.glLineWidth(1);
            GL11.glColor4f(custom ? 1 : 0, custom ? 0 : 1, 0, alpha);
            geometry.run();
        } finally {
            GL11.glPopAttrib();
        }
    }
}
