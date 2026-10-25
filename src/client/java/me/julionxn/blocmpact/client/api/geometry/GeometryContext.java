package me.julionxn.blocmpact.client.api.geometry;

import com.mojang.blaze3d.vertex.PoseStack;
import me.julionxn.blocmpact.client.api.Frame;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.function.Consumer;

/**
 * Level-2 drawing context for one frame. Geometry goes through Minecraft's render API (depth test against the world,
 * fog, improved transparency) and works with any backend.
 *
 * <pre>{@code
 * public void geometry(GeometryContext g) {
 *     g.draw(FxRenderTypes.additive(FxTextures.SOFT), m -> m.billboard(origin(g.frame().partial()), 2f, 0f, 0x80FFFFFF));
 * }
 * }</pre>
 */
public final class GeometryContext {
    private final Frame frame;
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;

    public GeometryContext(Frame frame, PoseStack poseStack, SubmitNodeCollector collector) {
        this.frame = frame;
        this.poseStack = poseStack;
        this.collector = collector;
    }

    public Frame frame() {
        return frame;
    }

    /** Raw access for advanced uses (models, items, text in the world). */
    public PoseStack poseStack() {
        return poseStack;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    /** Submits quads built by {@code builder} with {@code type}. */
    public void draw(RenderType type, Consumer<Mesh> builder) {
        collector.submitCustomGeometry(poseStack, type, (pose, vc) ->
            builder.accept(new Mesh(pose, vc, type.format(), frame.camera(), frame.right(), frame.up())));
    }

    /** Like {@link #draw} but rendered after the default order group (on top of other translucent submissions). */
    public void drawLate(RenderType type, Consumer<Mesh> builder) {
        collector.order(1).submitCustomGeometry(poseStack, type, (pose, vc) ->
            builder.accept(new Mesh(pose, vc, type.format(), frame.camera(), frame.right(), frame.up())));
    }
}
