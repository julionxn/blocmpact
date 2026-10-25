package me.julionxn.blocmpact.client.api.hud;

import me.julionxn.blocmpact.api.FxMath;
import me.julionxn.blocmpact.client.api.Frame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A fixed-height 2D canvas: whatever the GUI scale or window size, the canvas is {@link #height()} units tall and
 * {@link #width()} units wide (depends on the aspect ratio). Everything accepts fractional coordinates, so motion is
 * smooth. Colors are {@code 0xAARRGGBB}; alpha 0 draws nothing. Close it (try-with-resources) to restore the pose.
 */
public final class Canvas implements AutoCloseable {
    private final GuiGraphicsExtractor g;
    private final Frame frame;
    private final float height, width, scale;
    private final Font font;

    Canvas(GuiGraphicsExtractor g, Frame frame, float height) {
        this.g = g;
        this.frame = frame;
        this.height = height;
        this.scale = g.guiHeight() / height;
        this.width = g.guiWidth() / scale;
        this.font = Minecraft.getInstance().font;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
    }

    @Override
    public void close() {
        g.pose().popMatrix();
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public GuiGraphicsExtractor graphics() {
        return g;
    }

    // ---- shapes

    /** Filled rectangle. */
    public Canvas rect(float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || argb >>> 24 == 0) return this;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale(x1 - x0, y1 - y0);
        g.fill(0, 0, 1, 1, argb);
        g.pose().popMatrix();
        return this;
    }

    /** Vertical gradient. */
    public Canvas gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        if (x1 <= x0 || y1 <= y0) return this;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale((x1 - x0) / 64f, (y1 - y0) / 64f);
        g.fillGradient(0, 0, 64, 64, top, bottom);
        g.pose().popMatrix();
        return this;
    }

    /** Rectangle outline of the given thickness. */
    public Canvas outline(float x0, float y0, float x1, float y1, float thickness, int argb) {
        rect(x0, y0, x1, y0 + thickness, argb);
        rect(x0, y1 - thickness, x1, y1, argb);
        rect(x0, y0 + thickness, x0 + thickness, y1 - thickness, argb);
        return rect(x1 - thickness, y0 + thickness, x1, y1 - thickness, argb);
    }

    /** Line of a given thickness between two points (a rotated rectangle). */
    public Canvas line(float x0, float y0, float x1, float y1, float thickness, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f || argb >>> 24 == 0) return this;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.pose().translate(0, -thickness / 2);
        g.pose().scale(len, thickness);
        g.fill(0, 0, 1, 1, argb);
        g.pose().popMatrix();
        return this;
    }

    /** Four L-shaped corners around a rectangle (viewfinders, target boxes). */
    public Canvas corners(float x0, float y0, float x1, float y1, float length, float thickness, int argb) {
        rect(x0, y0, x0 + length, y0 + thickness, argb);
        rect(x0, y0, x0 + thickness, y0 + length, argb);
        rect(x1 - length, y0, x1, y0 + thickness, argb);
        rect(x1 - thickness, y0, x1, y0 + length, argb);
        rect(x0, y1 - thickness, x0 + length, y1, argb);
        rect(x0, y1 - length, x0 + thickness, y1, argb);
        rect(x1 - length, y1 - thickness, x1, y1, argb);
        return rect(x1 - thickness, y1 - length, x1, y1, argb);
    }

    /** Corner box centered on a point; animate {@code halfSize} from large to small to "lock on". */
    public Canvas reticle(float cx, float cy, float halfSize, float cornerLength, float thickness, int argb) {
        return corners(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, cornerLength, thickness, argb);
    }

    /** Horizontal progress bar. */
    public Canvas bar(float x0, float y0, float x1, float y1, float fill, int background, int foreground) {
        rect(x0, y0, x1, y1, background);
        return rect(x0, y0, x0 + (x1 - x0) * FxMath.clamp01(fill), y1, foreground);
    }

    /** Chevron arrow pointing at {@code angle} (radians, 0 = right). */
    public Canvas arrow(float x, float y, float angle, float size, float thickness, int argb) {
        float a1 = angle + 2.5f, a2 = angle - 2.5f;
        line(x, y, x + (float) Math.cos(a1) * size, y + (float) Math.sin(a1) * size, thickness, argb);
        return line(x, y, x + (float) Math.cos(a2) * size, y + (float) Math.sin(a2) * size, thickness, argb);
    }

    /** Draws a texture (whole) tinted by {@code argb}. Use {@code FxTextures} for circles, rings and soft dots. */
    public Canvas image(Identifier texture, float x0, float y0, float x1, float y1, int argb) {
        if (x1 <= x0 || y1 <= y0 || argb >>> 24 == 0) return this;
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale((x1 - x0) / 256f, (y1 - y0) / 256f);
        g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, 256, 256, 256, 256, argb);
        g.pose().popMatrix();
        return this;
    }

    /** Circle-ish shape from a texture centered on a point. */
    public Canvas imageCentered(Identifier texture, float cx, float cy, float radius, int argb) {
        return image(texture, cx - radius, cy - radius, cx + radius, cy + radius, argb);
    }

    // ---- text

    public float textWidth(String s, float scale) {
        return font.width(s) * scale;
    }

    public float lineHeight(float scale) {
        return font.lineHeight * scale;
    }

    /** Text with its top-left corner at (x, y). */
    public Canvas text(String s, float x, float y, float scale, int argb) {
        return text(s, x, y, scale, argb, false);
    }

    public Canvas text(String s, float x, float y, float scale, int argb, boolean shadow) {
        if (argb >>> 24 == 0 || s.isEmpty()) return this;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, argb, shadow);
        g.pose().popMatrix();
        return this;
    }

    public Canvas textCentered(String s, float cx, float y, float scale, int argb) {
        return text(s, cx - textWidth(s, scale) / 2, y, scale, argb);
    }

    public Canvas textRight(String s, float right, float y, float scale, int argb) {
        return text(s, right - textWidth(s, scale), y, scale, argb);
    }

    /** Centered text with extra spacing between letters (animate spacing from large to 1 to "close" a title). */
    public Canvas spaced(String s, float cx, float y, float scale, float spacing, int argb) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += font.width(String.valueOf(s.charAt(i))) + (i < s.length() - 1 ? spacing : 0);
        float x = cx - w * scale / 2;
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            text(c, x, y, scale, argb);
            x += (font.width(c) + spacing) * scale;
        }
        return this;
    }

    /** Centered text with a halo made of 8 offset copies in {@code haloArgb}, then the text in {@code coreArgb}. */
    public Canvas glow(String s, float cx, float y, float scale, int coreArgb, int haloArgb, float spread) {
        float x = cx - textWidth(s, scale) / 2;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) text(s, x + dx * spread * scale, y + dy * spread * scale, scale, haloArgb);
        return text(s, x, y, scale, coreArgb);
    }

    /** Text with a 1-unit outline in {@code outlineArgb}. */
    public Canvas outlined(String s, float x, float y, float scale, int argb, int outlineArgb) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) text(s, x + dx * scale * 0.5f, y + dy * scale * 0.5f, scale, outlineArgb);
        return text(s, x, y, scale, argb);
    }

    // ---- world anchoring

    /**
     * World point → canvas coordinates: (x, y, w); w ≤ 0 means behind the camera. Uses the matrices of the frame this
     * HUD is drawn over (bobbing and shake included).
     */
    public Vector3f project(Vec3 world) {
        if (frame == null) return new Vector3f(0, 0, -1);
        Vector3f p = frame.projectScreen(world);
        if (p.z <= 0) return p;
        return new Vector3f(p.x * width, p.y * height, p.z);
    }

    /**
     * Keeps a projected point inside the canvas with a margin. Returns (x, y, angle) where angle (radians) points
     * from the center towards the target when it had to be clamped, or NaN when it was already visible.
     */
    public Vector3f clampToEdge(Vector3f projected, float margin) {
        float cx = width / 2, cy = height / 2;
        float x = projected.x, y = projected.y;
        boolean behind = projected.z <= 0;
        if (behind) {
            x = width - x;
            y = height - y;
        }
        boolean inside = !behind && x >= margin && x <= width - margin && y >= margin && y <= height - margin;
        if (inside) return new Vector3f(x, y, Float.NaN);
        float dx = x - cx, dy = y - cy;
        if (Math.abs(dx) < 1e-4f && Math.abs(dy) < 1e-4f) dy = 1;
        float sx = (width / 2 - margin) / Math.max(Math.abs(dx), 1e-4f), sy = (height / 2 - margin) / Math.max(Math.abs(dy), 1e-4f);
        float s = Math.min(sx, sy);
        return new Vector3f(cx + dx * s, cy + dy * s, (float) Math.atan2(dy, dx));
    }
}
