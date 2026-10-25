package me.julionxn.blocmpact.client.api.hud;

import me.julionxn.blocmpact.client.api.Frame;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Level-1 drawing context. Draw on a fixed-size {@link Canvas} so the layout looks the same at any GUI scale:
 *
 * <pre>{@code
 * public void hud(HudContext h) {
 *     try (Canvas c = h.canvas(720)) {
 *         c.textCentered("HELLO", c.width() / 2, 80, 2f, 0xFFFFFFFF);
 *     }
 * }
 * }</pre>
 */
public final class HudContext {
    private final GuiGraphicsExtractor graphics;
    private final DeltaTracker delta;
    private final Frame frame;
    private final float partial;

    public HudContext(GuiGraphicsExtractor graphics, DeltaTracker delta, Frame frame, float partial) {
        this.graphics = graphics;
        this.delta = delta;
        this.frame = frame;
        this.partial = partial;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public DeltaTracker delta() {
        return delta;
    }

    /** The frame this HUD belongs to (for projecting world points); may be null before the first world frame. */
    public Frame frame() {
        return frame;
    }

    /** Partial tick of the frame being drawn. */
    public float partial() {
        return partial;
    }

    /** Opens a canvas {@code height} units tall. Close it when done. */
    public Canvas canvas(float height) {
        return new Canvas(graphics, frame, height);
    }
}
